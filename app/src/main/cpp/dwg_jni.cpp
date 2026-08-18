#include <jni.h>
#if defined(__ANDROID__)
#include <android/log.h>
#else
#include <cstdio>
#define ANDROID_LOG_INFO 4
inline int __android_log_print(int, const char*, const char*, ...) { return 0; }
#endif

#include <cmath>
#include <cstdlib>
#include <cstring>
#include <iomanip>
#include <sstream>
#include <string>
#include <unordered_set>
#include <vector>

extern "C" {
#include "dwg.h"
#include "dwg_api.h"
}

namespace {
constexpr const char* kTag = "CoreWallDWG";
constexpr std::size_t kMaxEntities = 200000;
constexpr int kMaxBlockDepth = 8;
constexpr double kPi = 3.14159265358979323846;

struct Pt { double x = 0.0; double y = 0.0; };
struct DrawStyle {
  int color_index = 256; // BYLAYER
  bool has_true_color = false;
  unsigned int true_color = 0;
  std::string line_type = "BYLAYER";
  int line_weight = 0;
};
struct Affine {
  double a = 1.0, b = 0.0, c = 0.0, d = 1.0, tx = 0.0, ty = 0.0;
  Pt point(Pt p) const { return {a * p.x + c * p.y + tx, b * p.x + d * p.y + ty}; }
  Pt vector(Pt p) const { return {a * p.x + c * p.y, b * p.x + d * p.y}; }
};

Affine compose(const Affine& parent, const Affine& local) {
  return {
      parent.a * local.a + parent.c * local.b,
      parent.b * local.a + parent.d * local.b,
      parent.a * local.c + parent.c * local.d,
      parent.b * local.c + parent.d * local.d,
      parent.a * local.tx + parent.c * local.ty + parent.tx,
      parent.b * local.tx + parent.d * local.ty + parent.ty};
}

bool finite(double value) { return std::isfinite(value); }
bool valid(Pt p) { return finite(p.x) && finite(p.y); }

std::string escapeJson(const char* input) {
  std::string out;
  if (!input) return out;
  for (const unsigned char* p = reinterpret_cast<const unsigned char*>(input); *p; ++p) {
    switch (*p) {
      case '\\': out += "\\\\"; break;
      case '"': out += "\\\""; break;
      case '\n': out += "\\n"; break;
      case '\r': out += "\\r"; break;
      case '\t': out += "\\t"; break;
      default:
        if (*p < 0x20) out += ' ';
        else out += static_cast<char>(*p);
    }
  }
  return out;
}

std::string jsonError(const std::string& message, const std::vector<std::string>& warnings = {}) {
  std::ostringstream out;
  out << "{\"ok\":false,\"error\":\"" << escapeJson(message.c_str()) << "\",\"warnings\":[";
  for (std::size_t i = 0; i < warnings.size(); ++i) {
    if (i) out << ',';
    out << "\"" << escapeJson(warnings[i].c_str()) << "\"";
  }
  out << "]}";
  return out.str();
}

class PayloadWriter {
 public:
  void addWarning(const std::string& warning) {
    if (warnings_.size() < 64) warnings_.push_back(warning);
  }

  std::string handleName(Dwg_Data* dwg, const char* table, Dwg_Object_Ref* handle, const std::string& fallback) {
    if (!dwg || !handle) return fallback;
    char* name = dwg_handle_name(dwg, table, handle);
    if (!name) return fallback.empty() ? "0" : fallback;
    std::string result(name);
    std::free(name); // dwg_handle_name always returns an allocated copy.
    return result.empty() ? fallback : result;
  }

  std::string layerFor(Dwg_Data* dwg, Dwg_Object_Entity* entity, const std::string& fallback) {
    if (!entity) return fallback.empty() ? "0" : fallback;
    return handleName(dwg, "LAYER", entity->layer, fallback.empty() ? "0" : fallback);
  }

  DrawStyle styleFor(Dwg_Data* dwg, Dwg_Object_Entity* entity) {
    DrawStyle style;
    if (!entity) return style;
    style.color_index = entity->color.index;
    if (entity->color.method == DWG_COLOR_METHOD_TRUECOLOR && (entity->color.rgb & 0x00ffffffu) != 0u) {
      style.has_true_color = true;
      style.true_color = entity->color.rgb & 0x00ffffffu;
    }
    style.line_type = handleName(dwg, "LTYPE", entity->ltype, "BYLAYER");
    style.line_weight = entity->linewt;
    return style;
  }

  DrawStyle styleFor(Dwg_Data* dwg, Dwg_Object_LAYER* layer) {
    DrawStyle style;
    if (!layer) return style;
    style.color_index = layer->color.index;
    if (layer->color.method == DWG_COLOR_METHOD_TRUECOLOR && (layer->color.rgb & 0x00ffffffu) != 0u) {
      style.has_true_color = true;
      style.true_color = layer->color.rgb & 0x00ffffffu;
    }
    style.line_type = handleName(dwg, "LTYPE", layer->ltype, "CONTINUOUS");
    style.line_weight = layer->linewt;
    return style;
  }

  void addLayers(Dwg_Data* dwg) {
    Dwg_Object_LAYER** layers = dwg_get_layers(dwg);
    if (!layers) return;
    for (std::size_t i = 0; layers[i]; ++i) {
      Dwg_Object_LAYER* layer = layers[i];
      const std::string name = layer->name ? layer->name : "0";
      if (layerNames_.insert(name).second) {
        const DrawStyle style = styleFor(dwg, layer);
        std::ostringstream item;
        item << "{\"name\":\"" << escapeJson(name.c_str()) << "\",\"colorIndex\":" << style.color_index;
        if (style.has_true_color) item << ",\"trueColor\":" << style.true_color;
        item << ",\"lineType\":\"" << escapeJson(style.line_type.c_str()) << "\",\"lineWeight\":" << style.line_weight
             << ",\"visible\":" << ((!layer->off && !layer->frozen) ? "true" : "false") << '}';
        layers_.push_back(item.str());
      }
    }
    std::free(layers);
  }

  void ensureLayer(const std::string& layer) {
    const std::string safe = layer.empty() ? "0" : layer;
    if (layerNames_.insert(safe).second) {
      layers_.push_back("{\"name\":\"" + escapeJson(safe.c_str()) + "\",\"colorIndex\":7,\"visible\":true}");
    }
  }

  bool canAdd() {
    if (entities_.size() >= kMaxEntities) {
      if (!entityCapReported_) addWarning("توقف المحرك بعد 200000 كيان لحماية الذاكرة");
      entityCapReported_ = true;
      return false;
    }
    return true;
  }

  void add(const std::string& type, const std::string& layer, const std::vector<double>& values,
           bool closed = false, const char* text = nullptr, const DrawStyle& style = DrawStyle()) {
    if (!canAdd()) return;
    for (double value : values) if (!finite(value)) { addWarning("تم تجاوز كيان بقيم هندسية غير صالحة"); return; }
    ensureLayer(layer);
    std::ostringstream item;
    item << "{\"type\":\"" << type << "\",\"layer\":\"" << escapeJson(layer.c_str()) << "\",\"values\":[";
    item << std::setprecision(17);
    for (std::size_t i = 0; i < values.size(); ++i) { if (i) item << ','; item << values[i]; }
    item << ']';
    if (closed) item << ",\"closed\":true";
    if (text) item << ",\"text\":\"" << escapeJson(text) << "\"";
    item << ",\"colorIndex\":" << style.color_index;
    if (style.has_true_color) item << ",\"trueColor\":" << style.true_color;
    item << ",\"lineType\":\"" << escapeJson(style.line_type.c_str()) << "\"";
    item << ",\"lineWeight\":" << style.line_weight;
    item << '}';
    entities_.push_back(item.str());
  }

  std::string finish(Dwg_Data* dwg) const {
    std::ostringstream out;
    out << "{\"ok\":true,\"insUnits\":" << (dwg ? dwg->header_vars.INSUNITS : 0) << ",\"warnings\":[";
    for (std::size_t i = 0; i < warnings_.size(); ++i) { if (i) out << ','; out << "\"" << escapeJson(warnings_[i].c_str()) << "\""; }
    out << "],\"layers\":[";
    for (std::size_t i = 0; i < layers_.size(); ++i) { if (i) out << ','; out << layers_[i]; }
    out << "],\"entities\":[";
    for (std::size_t i = 0; i < entities_.size(); ++i) { if (i) out << ','; out << entities_[i]; }
    out << "]}";
    return out.str();
  }

 private:
  std::vector<std::string> warnings_;
  std::vector<std::string> layers_;
  std::vector<std::string> entities_;
  std::unordered_set<std::string> layerNames_;
  bool entityCapReported_ = false;
};

Pt point2(double x, double y) { return {x, y}; }

void appendEntity(Dwg_Data* dwg, Dwg_Object* object, const Affine& transform,
                  const std::string& inheritedLayer, const DrawStyle& inheritedStyle,
                  int blockDepth, PayloadWriter& writer);

void appendPolyline(const std::vector<Pt>& points, bool closed, const Affine& transform,
                    const std::string& layer, const DrawStyle& style, PayloadWriter& writer) {
  if (points.size() < 2) return;
  std::vector<double> output;
  output.reserve(points.size() * 2);
  for (Pt point : points) {
    const Pt mapped = transform.point(point);
    if (!valid(mapped)) return;
    output.push_back(mapped.x); output.push_back(mapped.y);
  }
  writer.add("POLYLINE", layer, output, closed, nullptr, style);
}

void appendEllipse(const Pt& center, const Pt& major, const Pt& minor, double start, double end,
                   const Affine& transform, const std::string& layer, const DrawStyle& style,
                   PayloadWriter& writer) {
  const Pt c = transform.point(center);
  const Pt majorMapped = transform.vector(major);
  const Pt minorMapped = transform.vector(minor);
  if (!valid(c) || !valid(majorMapped) || !valid(minorMapped)) return;
  writer.add("ELLIPSE", layer, {c.x, c.y, majorMapped.x, majorMapped.y, minorMapped.x, minorMapped.y, start, end}, false, nullptr, style);
}

void appendCircleOrEllipse(const Pt& center, double radius, const Affine& transform,
                           const std::string& layer, const DrawStyle& style, PayloadWriter& writer) {
  if (!(radius > 0.0) || !finite(radius)) return;
  const Pt ex = transform.vector({radius, 0.0});
  const Pt ey = transform.vector({0.0, radius});
  const double lx = std::hypot(ex.x, ex.y);
  const double ly = std::hypot(ey.x, ey.y);
  const double dot = ex.x * ey.x + ex.y * ey.y;
  const Pt c = transform.point(center);
  if (!valid(c)) return;
  if (std::abs(lx - ly) <= 1e-9 * std::max(1.0, std::max(lx, ly)) && std::abs(dot) <= 1e-9 * std::max(1.0, lx * ly)) {
    writer.add("CIRCLE", layer, {c.x, c.y, (lx + ly) * 0.5}, false, nullptr, style);
  } else {
    appendEllipse(center, {radius, 0.0}, {0.0, radius}, 0.0, 2.0 * kPi, transform, layer, style, writer);
  }
}

void appendArcOrEllipse(const Pt& center, double radius, double startRad, double endRad,
                        const Affine& transform, const std::string& layer, const DrawStyle& style,
                        PayloadWriter& writer) {
  const Pt ex = transform.vector({radius, 0.0});
  const Pt ey = transform.vector({0.0, radius});
  const double lx = std::hypot(ex.x, ex.y);
  const double ly = std::hypot(ey.x, ey.y);
  const double dot = ex.x * ey.x + ex.y * ey.y;
  const double determinant = transform.a * transform.d - transform.b * transform.c;
  const Pt c = transform.point(center);
  if (!valid(c) || !(radius > 0.0)) return;
  if (determinant > 0.0 && std::abs(lx - ly) <= 1e-9 * std::max(1.0, std::max(lx, ly)) && std::abs(dot) <= 1e-9 * std::max(1.0, lx * ly)) {
    // الدوران جزء من التحويل. من دونه يظهر ARC داخل INSERT في موضع صحيح لكن بزاوية خاطئة.
    const double rotation = std::atan2(ex.y, ex.x);
    writer.add("ARC", layer, {c.x, c.y, (lx + ly) * 0.5,
                               (startRad + rotation) * 180.0 / kPi,
                               (endRad + rotation) * 180.0 / kPi}, false, nullptr, style);
  } else {
    // المقياس غير المنتظم أو الانعكاس يحوّل القوس إلى قطع ناقص؛ نحفظ المحورين
    // ومعاملات القوس بدقة بدلاً من رسم قوس دائري مزيف.
    appendEllipse(center, {radius, 0.0}, {0.0, radius}, startRad, endRad, transform, layer, style, writer);
  }
}

std::vector<Pt> expandLwPolyline(const Dwg_Entity_LWPOLYLINE* entity) {
  std::vector<Pt> source;
  if (!entity || !entity->points) return source;
  source.reserve(entity->num_points);
  for (BITCODE_BL i = 0; i < entity->num_points; ++i) source.push_back(point2(entity->points[i].x, entity->points[i].y));
  if (source.size() < 2 || !entity->bulges || entity->num_bulges == 0) return source;

  const bool closed = (entity->flag & 512u) != 0;
  std::vector<Pt> output;
  output.reserve(source.size() * 2);
  const std::size_t segmentCount = closed ? source.size() : source.size() - 1;
  for (std::size_t index = 0; index < segmentCount; ++index) {
    const Pt start = source[index];
    const Pt end = source[(index + 1) % source.size()];
    output.push_back(start);
    const double bulge = index < entity->num_bulges ? entity->bulges[index] : 0.0;
    if (std::abs(bulge) < 1e-12) continue;
    const double dx = end.x - start.x, dy = end.y - start.y;
    const double chord = std::hypot(dx, dy);
    const double sweep = 4.0 * std::atan(bulge);
    const double tangent = std::tan(sweep * 0.5);
    if (chord < 1e-12 || std::abs(tangent) < 1e-12) continue;
    const Pt mid{(start.x + end.x) * 0.5, (start.y + end.y) * 0.5};
    const Pt normal{-dy / chord, dx / chord};
    const Pt center{mid.x + normal.x * chord / (2.0 * tangent), mid.y + normal.y * chord / (2.0 * tangent)};
    const double radius = std::hypot(start.x - center.x, start.y - center.y);
    const double begin = std::atan2(start.y - center.y, start.x - center.x);
    const int steps = std::max(1, static_cast<int>(std::ceil(std::abs(sweep) / (kPi / 18.0))));
    for (int step = 1; step < steps; ++step) {
      const double angle = begin + sweep * static_cast<double>(step) / steps;
      output.push_back({center.x + radius * std::cos(angle), center.y + radius * std::sin(angle)});
    }
  }
  if (!closed) output.push_back(source.back());
  return output;
}

void appendBlock(Dwg_Data* dwg, Dwg_Entity_INSERT* insert, const Affine& parent,
                 const std::string& inheritedLayer, const DrawStyle& inheritedStyle,
                 int blockDepth, PayloadWriter& writer) {
  if (blockDepth >= kMaxBlockDepth) { writer.addWarning("تم تجاوز BLOCK متداخل بعمق أكبر من 8"); return; }
  if (!insert || !insert->block_header) { writer.addWarning("INSERT بلا مرجع BLOCK صالح"); return; }
  Dwg_Object* block = dwg_ref_object(dwg, insert->block_header);
  if (!block) { writer.addWarning("تعذر حل مرجع BLOCK داخل INSERT"); return; }
  const double sx = std::abs(insert->scale.x) < 1e-12 ? 1.0 : insert->scale.x;
  const double sy = std::abs(insert->scale.y) < 1e-12 ? 1.0 : insert->scale.y;
  const double angle = insert->rotation;
  const double co = std::cos(angle), si = std::sin(angle);
  const Affine local{co * sx, si * sx, -si * sy, co * sy, insert->ins_pt.x, insert->ins_pt.y};
  const std::string insertLayer = writer.layerFor(dwg, insert->parent, inheritedLayer);
  DrawStyle insertStyle = writer.styleFor(dwg, insert->parent);
  if (insertStyle.color_index == 0) insertStyle = inheritedStyle;
  // BLOCK_HEADER يملك الكيانات بالطريقة العامة نفسها؛ الواجهة العامة متاحة
  // في جميع حزم LibreDWG، بخلاف دوال block المساعدة غير المصدّرة في بعض البنى.
  for (Dwg_Object* item = get_first_owned_entity(block); item; item = get_next_owned_entity(block, item)) {
    appendEntity(dwg, item, compose(parent, local), insertLayer, insertStyle, blockDepth + 1, writer);
  }
}

void appendDimensionBlock(Dwg_Data* dwg, Dwg_Object_Ref* blockRef, const Affine& transform,
                          const std::string& layer, const DrawStyle& style, int blockDepth,
                          PayloadWriter& writer) {
  if (blockDepth >= kMaxBlockDepth || !blockRef) return;
  Dwg_Object* block = dwg_ref_object(dwg, blockRef);
  if (!block) { writer.addWarning("تعذر حل BLOCK البعد في DWG"); return; }
  for (Dwg_Object* item = get_first_owned_entity(block); item; item = get_next_owned_entity(block, item)) {
    appendEntity(dwg, item, transform, layer, style, blockDepth + 1, writer);
  }
}

void appendSpline(const Dwg_Entity_SPLINE* entity, const Affine& transform, const std::string& layer,
                  const DrawStyle& style, PayloadWriter& writer) {
  if (!entity) return;
  std::vector<Pt> points;
  if (entity->fit_pts && entity->num_fit_pts >= 2) {
    points.reserve(entity->num_fit_pts);
    for (BITCODE_BS i = 0; i < entity->num_fit_pts; ++i) points.push_back(point2(entity->fit_pts[i].x, entity->fit_pts[i].y));
  } else if (entity->ctrl_pts && entity->num_ctrl_pts >= 2) {
    points.reserve(entity->num_ctrl_pts);
    for (BITCODE_BL i = 0; i < entity->num_ctrl_pts; ++i) points.push_back(point2(entity->ctrl_pts[i].x, entity->ctrl_pts[i].y));
  }
  // مسار التحكم ليس تقييم NURBS كاملاً، لكنه يمنع اختفاء المنحنى عند قراءة ملف 2D.
  appendPolyline(points, entity->closed_b != 0, transform, layer, style, writer);
}

void appendLeader(const Dwg_Entity_LEADER* entity, const Affine& transform, const std::string& layer,
                  const DrawStyle& style, PayloadWriter& writer) {
  if (!entity || !entity->points || entity->num_points < 2) return;
  std::vector<Pt> points;
  points.reserve(entity->num_points);
  for (BITCODE_BL i = 0; i < entity->num_points; ++i) points.push_back(point2(entity->points[i].x, entity->points[i].y));
  appendPolyline(points, false, transform, layer, style, writer);
}

void appendHatch(const Dwg_Entity_HATCH* entity, const Affine& transform, const std::string& layer,
                 const DrawStyle& style, PayloadWriter& writer) {
  if (!entity || !entity->paths) return;
  for (BITCODE_BL pathIndex = 0; pathIndex < entity->num_paths; ++pathIndex) {
    const Dwg_HATCH_Path& path = entity->paths[pathIndex];
    if ((path.flag & 2u) != 0u && path.polyline_paths && path.num_segs_or_paths >= 2) {
      std::vector<Pt> points;
      points.reserve(path.num_segs_or_paths);
      for (BITCODE_BL i = 0; i < path.num_segs_or_paths; ++i) {
        points.push_back(point2(path.polyline_paths[i].point.x, path.polyline_paths[i].point.y));
      }
      appendPolyline(points, path.closed != 0, transform, layer, style, writer);
      continue;
    }
    if (!path.segs) continue;
    for (BITCODE_BL segIndex = 0; segIndex < path.num_segs_or_paths; ++segIndex) {
      const Dwg_HATCH_PathSeg& seg = path.segs[segIndex];
      switch (seg.curve_type) {
        case 1: appendPolyline({point2(seg.first_endpoint.x, seg.first_endpoint.y), point2(seg.second_endpoint.x, seg.second_endpoint.y)}, false, transform, layer, style, writer); break;
        case 2: appendArcOrEllipse(point2(seg.center.x, seg.center.y), seg.radius, seg.start_angle, seg.end_angle, transform, layer, style, writer); break;
        case 3: {
          const Pt major = point2(seg.endpoint.x - seg.center.x, seg.endpoint.y - seg.center.y);
          const Pt minor = {-major.y * seg.minor_major_ratio, major.x * seg.minor_major_ratio};
          appendEllipse(point2(seg.center.x, seg.center.y), major, minor, seg.start_angle, seg.end_angle, transform, layer, style, writer);
          break;
        }
        case 4: {
          std::vector<Pt> points;
          if (seg.fitpts && seg.num_fitpts >= 2) {
            for (BITCODE_BL i = 0; i < seg.num_fitpts; ++i) points.push_back(point2(seg.fitpts[i].x, seg.fitpts[i].y));
          } else if (seg.control_points && seg.num_control_points >= 2) {
            for (BITCODE_BL i = 0; i < seg.num_control_points; ++i) points.push_back(point2(seg.control_points[i].point.x, seg.control_points[i].point.y));
          }
          appendPolyline(points, false, transform, layer, style, writer);
          break;
        }
        default: writer.addWarning("تم تجاوز حد HATCH بنوع منحنى غير معروف"); break;
      }
    }
  }
}

void appendEntity(Dwg_Data* dwg, Dwg_Object* object, const Affine& transform,
                  const std::string& inheritedLayer, const DrawStyle& inheritedStyle,
                  int blockDepth, PayloadWriter& writer) {
  if (!object || !object->tio.entity) return;
  Dwg_Object_Entity* common = object->tio.entity;
  std::string layer = writer.layerFor(dwg, common, inheritedLayer);
  if (layer == "0" && !inheritedLayer.empty()) layer = inheritedLayer;
  DrawStyle style = writer.styleFor(dwg, common);
  if (style.color_index == 0) style = inheritedStyle;
  if (style.line_type == "BYBLOCK") style.line_type = inheritedStyle.line_type;

  switch (object->fixedtype) {
    case DWG_TYPE_LINE: {
      auto* e = common->tio.LINE; if (!e) return;
      const Pt a = transform.point(point2(e->start.x, e->start.y)); const Pt b = transform.point(point2(e->end.x, e->end.y));
      if (valid(a) && valid(b)) writer.add("LINE", layer, {a.x, a.y, b.x, b.y}, false, nullptr, style);
      break;
    }
    case DWG_TYPE_LWPOLYLINE: {
      auto* e = common->tio.LWPOLYLINE; if (!e || !e->points) return;
      const std::vector<Pt> points = expandLwPolyline(e);
      // LibreDWG يعيد ترميز bit الإغلاق من DXF 1 إلى 512 داخل حقل flag.
      appendPolyline(points, (e->flag & 512u) != 0, transform, layer, style, writer);
      break;
    }
    case DWG_TYPE_POLYLINE_2D: {
      auto* e = common->tio.POLYLINE_2D; if (!e || !e->vertex) return;
      std::vector<Pt> points;
      for (BITCODE_BL i = 0; i < e->num_owned; ++i) {
        Dwg_Object* vertex = dwg_ref_object(dwg, e->vertex[i]);
        if (vertex && vertex->fixedtype == DWG_TYPE_VERTEX_2D && vertex->tio.entity && vertex->tio.entity->tio.VERTEX_2D) {
          const auto* v = vertex->tio.entity->tio.VERTEX_2D; points.push_back(point2(v->point.x, v->point.y));
        }
      }
      appendPolyline(points, (e->flag & 1u) != 0, transform, layer, style, writer);
      break;
    }
    case DWG_TYPE_POLYLINE_3D: {
      auto* e = common->tio.POLYLINE_3D; if (!e || !e->vertex) return;
      std::vector<Pt> points;
      for (BITCODE_BL i = 0; i < e->num_owned; ++i) {
        Dwg_Object* vertex = dwg_ref_object(dwg, e->vertex[i]);
        if (vertex && vertex->fixedtype == DWG_TYPE_VERTEX_3D && vertex->tio.entity && vertex->tio.entity->tio.VERTEX_3D) {
          const auto* v = vertex->tio.entity->tio.VERTEX_3D; points.push_back(point2(v->point.x, v->point.y));
        }
      }
      appendPolyline(points, false, transform, layer, style, writer);
      break;
    }
    case DWG_TYPE_CIRCLE: {
      auto* e = common->tio.CIRCLE; if (e) appendCircleOrEllipse(point2(e->center.x, e->center.y), e->radius, transform, layer, style, writer);
      break;
    }
    case DWG_TYPE_ARC: {
      auto* e = common->tio.ARC; if (e) appendArcOrEllipse(point2(e->center.x, e->center.y), e->radius, e->start_angle, e->end_angle, transform, layer, style, writer);
      break;
    }
    case DWG_TYPE_ELLIPSE: {
      auto* e = common->tio.ELLIPSE;
      if (e) {
        const Pt major = point2(e->sm_axis.x, e->sm_axis.y);
        const Pt minor = {-major.y * e->axis_ratio, major.x * e->axis_ratio};
        appendEllipse(point2(e->center.x, e->center.y), major, minor, e->start_angle, e->end_angle, transform, layer, style, writer);
      }
      break;
    }
    case DWG_TYPE_POINT: {
      auto* e = common->tio.POINT; if (!e) return;
      const Pt p = transform.point(point2(e->x, e->y)); if (valid(p)) writer.add("POINT", layer, {p.x, p.y}, false, nullptr, style);
      break;
    }
    case DWG_TYPE_TEXT: {
      auto* e = common->tio.TEXT; if (!e) return;
      const Pt p = transform.point(point2(e->ins_pt.x, e->ins_pt.y));
      const double scale = std::sqrt(std::abs(transform.a * transform.d - transform.b * transform.c));
      if (valid(p)) writer.add("TEXT", layer, {p.x, p.y, e->height * scale, e->rotation * 180.0 / kPi}, false, e->text_value, style);
      break;
    }
    case DWG_TYPE_MTEXT: {
      auto* e = common->tio.MTEXT; if (!e) return;
      const Pt p = transform.point(point2(e->ins_pt.x, e->ins_pt.y));
      const double rotation = std::atan2(e->x_axis_dir.y, e->x_axis_dir.x) * 180.0 / kPi;
      const double scale = std::sqrt(std::abs(transform.a * transform.d - transform.b * transform.c));
      if (valid(p)) writer.add("TEXT", layer, {p.x, p.y, e->text_height * scale, rotation}, false, e->text, style);
      break;
    }
    case DWG_TYPE_ATTDEF: {
      auto* e = common->tio.ATTDEF; if (!e || (e->flags & 1u) != 0u) return;
      const Pt p = transform.point(point2(e->ins_pt.x, e->ins_pt.y));
      const double scale = std::sqrt(std::abs(transform.a * transform.d - transform.b * transform.c));
      if (valid(p)) writer.add("TEXT", layer, {p.x, p.y, e->height * scale, e->rotation * 180.0 / kPi}, false, e->default_value, style);
      break;
    }
    case DWG_TYPE_SPLINE:
      appendSpline(common->tio.SPLINE, transform, layer, style, writer);
      break;
    case DWG_TYPE_LEADER:
      appendLeader(common->tio.LEADER, transform, layer, style, writer);
      break;
    case DWG_TYPE_HATCH:
      appendHatch(common->tio.HATCH, transform, layer, style, writer);
      break;
    case DWG_TYPE_DIMENSION_LINEAR: {
      auto* e = common->tio.DIMENSION_LINEAR;
      if (e) appendDimensionBlock(dwg, e->block, transform, layer, style, blockDepth, writer);
      break;
    }
    case DWG_TYPE_DIMENSION_ALIGNED: {
      auto* e = common->tio.DIMENSION_ALIGNED;
      if (e) appendDimensionBlock(dwg, e->block, transform, layer, style, blockDepth, writer);
      break;
    }
    case DWG_TYPE_INSERT:
      appendBlock(dwg, common->tio.INSERT, transform, layer, style, blockDepth, writer);
      break;
    default:
      writer.addWarning(std::string("تم تجاوز كيان DWG غير مدعوم: ") + (object->name ? object->name : "UNKNOWN"));
      break;
  }
}

std::string loadDwg(const char* path) {
  if (!path || !*path) return jsonError("مسار DWG فارغ");
  Dwg_Data dwg{};
  const int result = dwg_read_file(path, &dwg);
  const int fatal = DWG_ERR_CRITICAL | DWG_ERR_INVALIDDWG | DWG_ERR_IOERROR | DWG_ERR_OUTOFMEM;
  if (result & fatal) {
    dwg_free(&dwg);
    return jsonError("LibreDWG رفض ملف DWG أو تعذر الوصول إليه");
  }
  PayloadWriter writer;
  if (result != 0) writer.addWarning("اكتملت القراءة مع تحذيرات LibreDWG؛ تم عرض الهندسة المتاحة فقط");
  writer.addLayers(&dwg);
  Dwg_Object_Ref* model = dwg_model_space_ref(&dwg);
  if (!model) {
    dwg_free(&dwg);
    return jsonError("ملف DWG لا يحتوي على Model Space صالح");
  }
  for (Dwg_Object* entity = get_first_owned_entity(model->obj); entity; entity = get_next_owned_entity(model->obj, entity)) {
    appendEntity(&dwg, entity, Affine{}, "", DrawStyle{}, 0, writer);
  }
  const std::string payload = writer.finish(&dwg);
  dwg_free(&dwg); // تحرير كل الذاكرة التي خصصها LibreDWG قبل العودة إلى Kotlin.
  return payload;
}
} // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_corewall_qaqc_ui_cad_NativeDwgBridge_readDwgPayload(JNIEnv* env, jobject, jstring path) {
  const char* utfPath = path ? env->GetStringUTFChars(path, nullptr) : nullptr;
  const std::string payload = loadDwg(utfPath);
  if (utfPath) env->ReleaseStringUTFChars(path, utfPath);
  __android_log_print(ANDROID_LOG_INFO, kTag, "DWG parsed locally: %zu bytes payload", payload.size());
  return env->NewStringUTF(payload.c_str());
}

// لا يدخل APK. يسمح بتشغيل نفس محول JNI على ملفات DWG مرجعية في بيئة البناء.
#if defined(COREWALL_DWG_HOST_TEST)
int main(int argc, char** argv) {
  if (argc != 2) return 64;
  const std::string payload = loadDwg(argv[1]);
  std::fputs(payload.c_str(), stdout);
  return payload.find("\"ok\":true") != std::string::npos ? 0 : 1;
}
#endif
