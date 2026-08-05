# CoreWall — دراسة معمارية للتحوّل إلى AI Construction Agent

**تقرير CTO · أغسطس 2026**
**المُعِدّ:** مراجعة معمارية مبنية على قراءة الكود الفعلي (v7.1 / Build 28)

---

## ملخّص تنفيذي (Executive Summary)

راجعت الكود الحقيقي لـCoreWall، مش الوصف. الخلاصة في خمس نقط:

| # | الحكم | التفصيل |
|---|---|---|
| 1 | **المعمارية المقترحة over-engineered بشكل خطير** | البيانات المنظّمة للمشروع كله **أقل من 500 كيلوبايت**. Vector DB + Mem0 + Multi-Agent لحجم ده زي ما تبني Data Warehouse عشان تخزّن ملف Excel |
| 2 | **مقارنة الـRevisions مش مشكلة RAG أصلاً** | `W7: 8T25 → 10T25` **مستحيل** تلاقيها بـcosine similarity. دي مشكلة ETL + Diff حتمي. استخدام RAG هنا **الأداة الغلط تماماً** |
| 3 | **السيرفر مطلوب فعلاً — بس لأسباب تانية** | استخراج الرسومات، التخزين المشترك، Vision، إدارة المفاتيح، تعدّد المستخدمين. مش للبحث الدلالي |
| 4 | **العائق الحقيقي للتسويق مش الـAI — هو Multi-tenancy** | التطبيق حالياً **مقفول على مشروع واحد** مخزّن في `assets/`. مش قادر يفتح مشروع تاني أصلاً |
| 5 | **المشروع قابل للتسويق — والميزة التنافسية مش الـAI** | الميزة هي **نموذج المجال** (منطق المدايات، الفجوات، عزل الأدوار) اللي اتبنى بالفعل. ده اللي Procore وAutodesk مش عندهم |

**التوصية الأساسية:** ابنِ السيرفر، بس **ابدأ بـPostgreSQL + pgvector + FastAPI + خط استخراج حتمي**. أجّل Qdrant وMem0 وLangGraph لحد ما يكون عندك سبب قياسي يبرّرهم. ده هيوفّر ~70% من وقت التطوير و~60% من تكلفة التشغيل في السنة الأولى.

---

## 1. تقييم المعمارية الحالية

### 1.1 اللي اتبنى صح (وده مش قليل)

قرأت الكود. فيه قرارات معمارية **ممتازة** لازم **تتحافظ عليها** في أي تصميم جديد:

| القرار | التقييم | ليه مهم |
|---|---|---|
| **"التطبيق بيحسب، الـAI بيفسّر"** | ⭐ ممتاز | ده **بالظبط** المبدأ الصح لمجال حرج. معظم مشاريع الـAI في الإنشاءات بتفشل لأنها بتخلّي الـLLM يحسب |
| `domain/` منطق خالص من غير أندرويد | ⭐ ممتاز | ينفع **ينتقل للسيرفر بـcopy-paste** — Kotlin نفسه أو يتترجم لـPython |
| الجدول المرجعي read-only + patches فوقه | ⭐ ممتاز | Event-sourcing مبسّط. الأصل مابيضيعش أبداً |
| عزل الأدوار كـinvariant | ⭐ ممتاز | بيمنع تسرّب بيانات بين الأدوار — ده أخطر bug ممكن في المجال ده |
| صفر مكتبات خارجية للشبكة | ✅ جيد للموبايل | APK أصغر، تبعيات أقل |
| بوابة موافقة على الإجراءات المدمّرة | ⭐ ممتاز | القرار الصح في مجال فيه سجلّات جودة |

**الاستنتاج:** `ScheduleLogic` و`FloorComparison` و`SteelCalculator` هم **الأصل الحقيقي** للمشروع، مش طبقة الـAI. دول لازم يبقوا **نواة السيرفر**، مش يتعادوا كتابة.

### 1.2 النواقص الحقيقية

| # | النقص | الخطورة | التأثير |
|---|---|---|---|
| 1 | **مشروع واحد مقفول في `assets/`** | 🔴 حرج | مستحيل تبيعه لعميل تاني من غير إعادة بناء |
| 2 | **مفيش مستخدمين ولا صلاحيات** | 🔴 حرج | مفيش "مين عمل إيه" — ده مطلب تعاقدي في QA/QC |
| 3 | **مفيش مزامنة** | 🔴 حرج | كل موبايل جزيرة. مهندسين اتنين على نفس الدور = بيانات متضاربة |
| 4 | **مفتاح API على الجهاز** | 🟠 عالي | كل مستخدم بيدفع بنفسه؛ مفيش تحكّم في التكلفة ولا rate limiting |
| 5 | **مفيش Revisions للرسومات** | 🟠 عالي | جوهر الطلب. حالياً الملف بيتحلّل مرة وخلاص — **مفيش نموذج نسخ أصلاً** |
| 6 | **الـLLM بيستخرج بيانات الجداول** | 🟠 عالي | استخراج BBS بالـLLM = عرضة للهلوسة. مفيش تحقق ولا schema validation |
| 7 | **DWG مش مدعوم** | 🟡 متوسط | صيغة مغلقة — بس دي الصيغة السايدة في المكاتب الفنية |
| 8 | **مفيش تقييم آلي (evals)** | 🟠 عالي | مفيش طريقة تعرف إن التحديث حسّن ولا خرّب |
| 9 | **CI بيعمل compile بس** | 🟡 متوسط | أخطاء وقت التشغيل بتعدّي (وحصل فعلاً — crash في v5.1) |

### 1.3 الحكم على المعمارية المقترحة

```
Android → AI Server → LangGraph → [Document | Vision | QA/QC Agents]
                                        ↓
                          Qdrant + Mem0 + PostgreSQL
```

**التقييم البند ببند:**

| المكوّن | الحكم | السبب |
|---|---|---|
| **AI Server (Python)** | ✅ **صح** | مبرّر بقوة: استخراج ثقيل، مفاتيح مركزية، تخزين مشترك |
| **PostgreSQL** | ✅ **صح** | لازم. ده هيبقى مصدر الحقيقة |
| **LangGraph** | 🟡 **صح بس بدري** | القيمة الحقيقية في checkpointing + HITL. مش محتاجه في Phase 1 |
| **Qdrant** | 🔴 **غلط دلوقتي** | حجم بياناتك **أقل من 1M vector بكتير**. `pgvector` كفاية وبيوفّر خدمة كاملة |
| **Mem0** | 🔴 **غلط** | "الذاكرة" عندك = حالة مشروع منظّمة، مكانها PostgreSQL. Mem0 بيحل مشكلة مساعدين عامّين |
| **3 Agents منفصلين** | 🟠 **مبالغة** | Multi-agent بيضيف أوضاع فشل تنسيقية. الأنسب: **Orchestrator واحد + عمّال حتميين** |
| **Docling** | ✅ **صح** | 61k نجمة، مدعوم من IBM، ومصمّم لخطوط الـAI |
| **OCR (PaddleOCR/Surya)** | ✅ **صح** | لازم للرسومات الممسوحة ضوئياً |
| **OpenRouter** | ✅ **صح** | مضاف بالفعل — يديك مرونة تبديل الموديلات |

---

## 2. التصحيح المعماري الجوهري

### 2.1 المشكلة اللي لازم تتفهم صح

الطلب بيقول: عايز النظام يكتشف إن `Wall W7` اتغيّر من `8T25` لـ`10T25` بين Revision 04 و05.

**دي مش مشكلة بحث دلالي. دي مشكلة استخراج + مقارنة.**

| المقاربة | إيه اللي هيحصل |
|---|---|
| ❌ **RAG/Vector Search** | هتدوّر على "نص شبيه". هترجّع لك مقاطع فيها `W7` من النسختين. الـLLM هيحاول يقارنهم في السياق. **النتيجة غير حتمية، وبتفشل مع 200 عنصر** |
| ✅ **Structured Extraction + Diff** | تستخرج **كل** نسخة لنفس الـschema، تعمل `JOIN` على `(project, floor, mark)`، وتقارن الحقول. **حتمي، كامل، وقابل للتدقيق** |

**القاعدة العامة:**

> **البيانات المنظّمة (Schedules, BBS, Quantities) → قاعدة بيانات علائقية + diff حتمي.**
> **البيانات غير المنظّمة (Specs, Method Statements, Minutes, Correspondence) → RAG.**

الـLLM دوره في المسار الأول **مترجِم فقط**: يحوّل PDF → JSON منظّم. بعد كده يخرج من الصورة تماماً.

### 2.2 المعمارية الموصى بها

```
┌─────────────────────────────────────────────────────────────────┐
│                        ANDROID CLIENT                            │
│  Compose UI · Room (offline cache) · Deterministic domain logic  │
│  يفضل شغّال أوفلاين بالكامل على آخر نسخة متزامنة                  │
└───────────────────────────┬─────────────────────────────────────┘
                            │ HTTPS · JWT · Delta sync
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                      API GATEWAY (FastAPI)                       │
│         Auth · Rate limit · Multi-tenant scoping · Audit         │
└───────────────────────────┬─────────────────────────────────────┘
                            │
        ┌───────────────────┼───────────────────┐
        ▼                   ▼                   ▼
┌───────────────┐  ┌────────────────┐  ┌──────────────────┐
│ SYNC SERVICE  │  │  AGENT SERVICE │  │ INGESTION QUEUE  │
│ Delta · CRDT- │  │  Orchestrator  │  │  Celery/ARQ      │
│ lite conflict │  │  + Tools       │  │  Retry · DLQ     │
└───────┬───────┘  └────────┬───────┘  └────────┬─────────┘
        │                   │                   │
        │                   │                   ▼
        │                   │      ┌────────────────────────────┐
        │                   │      │   EXTRACTION PIPELINE      │
        │                   │      │  (حتمي، مش وكيل)            │
        │                   │      │                            │
        │                   │      │  1. Classify document      │
        │                   │      │  2. Docling / OCR          │
        │                   │      │  3. LLM → strict JSON      │
        │                   │      │  4. Schema validation ⚠    │
        │                   │      │  5. Domain validation ⚠    │
        │                   │      │  6. Confidence gate        │
        │                   │      │  7. Persist + version      │
        │                   │      │  8. DIFF vs prev revision  │
        │                   │      │  9. Emit change events     │
        │                   │      └────────────┬───────────────┘
        │                   │                   │
        └───────────────────┴───────────────────┘
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                      PERSISTENCE LAYER                           │
│                                                                  │
│  PostgreSQL 16  ─── مصدر الحقيقة الوحيد                          │
│    ├── Structured: projects, floors, elements, revisions,        │
│    │                schedules, wir, photos, issues, tasks        │
│    ├── pgvector:   embeddings للمستندات النصّية غير المنظّمة        │
│    └── Audit log:  كل تغيير، مين وإمتى وليه                       │
│                                                                  │
│  S3/MinIO ─── الملفات الخام (رسومات، صور، تقارير)                 │
└─────────────────────────────────────────────────────────────────┘
```

**الفرق الجوهري عن المقترح الأصلي:**

| المقترح | الموصى به | التبرير |
|---|---|---|
| 3 Agents مستقلين | **Orchestrator + Deterministic Workers** | استخراج الرسمة مش قرار — ده pipeline. الوكيل بيتصرّف بس لما يكون فيه اختيار |
| Qdrant منفصل | **pgvector جوّه PostgreSQL** | خدمة أقل، transaction واحد، وnative joins مع البيانات المنظّمة |
| Mem0 | **PostgreSQL + conversation summaries** | "الذاكرة" في مجالك = حالة مشروع، مش تفضيلات مستخدم |
| RAG لكل حاجة | **RAG للنصّي فقط، SQL للمنظّم** | دقة أعلى وتكلفة أقل |

---

## 3. مقارنة التقنيات

### 3.1 Agent Orchestration

| المعيار | LangGraph | CrewAI | AutoGen |
|---|---|---|---|
| النموذج | Graph صريح، حالة صريحة | أدوار وفِرَق | محادثات متعدّدة الأطراف |
| Checkpointing / استئناف | ⭐ الأقوى | محدود | محدود |
| Human-in-the-loop | ⭐ مدمج | أساسي | أساسي |
| Observability | ⭐ ممتاز (OpenTelemetry per node) | متوسط | متوسط |
| منحنى التعلّم | عالي | **الأقل** | متوسط |
| استهلاك التوكنز | الأقل | **حتى 3× أعلى** | متوسط |
| الحالة (2026) | نشط، معيار فعلي للإنتاج | نشط | **مايكروسوفت حوّلته لوضع الصيانة** |

**التوصية: LangGraph — بس مش من Phase 1.**

السبب: مجالك فيه **موافقة بشرية إجبارية** (زي ما عملت في التطبيق) و**عمليات طويلة** (استخراج رسمة ممكن ياخد دقايق). دي بالظبط اللي LangGraph بيتفوّق فيها. لكن في Phase 1 محتاج pipeline خطّي، وده Python عادي.

❌ **AutoGen مرفوض** — وضع الصيانة يعني مخاطرة على منتج تجاري.

> **ملاحظة مهمة:** الفرق بين نظام وكلاء كويس ووحش **نادراً** ما يكون الـframework. الفرق في **خط التقييم (evals)، والمراقبة، ومنطق التعافي من الفشل**. ابنِ التلاتة دول وهتبقى قدّام 80% من الفرق.

### 3.2 Vector Database

| المعيار | pgvector | Qdrant | Chroma | Pinecone |
|---|---|---|---|---|
| خدمة إضافية؟ | **لا** | نعم | نعم | SaaS فقط |
| Self-host | ✅ | ✅ Apache-2.0 | ✅ Apache-2.0 | ❌ (serverless إجباري 2026) |
| Join مع بيانات علائقية | ⭐ **native SQL** | تطبيقي | تطبيقي | تطبيقي |
| Transaction واحد | ⭐ نعم | لا | لا | لا |
| الأداء عند <1M vector | كافٍ تماماً | ممتاز | جيد | ممتاز |
| الأداء عند >10M | يبدأ يتعب | ⭐ ممتاز | ضعيف | ممتاز |
| التكلفة السحابية | ضمن الـDB | ~$57/GB RAM شهرياً · Free: 1GB | Free حتى 1M embedding | $0.33/GB + read/write units |

**التوصية: ابدأ بـ`pgvector`. انتقل لـQdrant لما تعدّي ~5M vector أو تحتاج فلترة معقّدة على الـpayload.**

**الحساب اللي بيبرّر ده:**

مشروع زي BHR Tower 1:
- 48 دور × ~40 مستند = ~2,000 مستند
- متوسط 20 chunk للمستند = **~40,000 vector**
- بُعد 1024 × 4 بايت = ~160 ميجابايت

**40 ألف vector.** حدود pgvector العملية عشرات الملايين. حتى **50 مشروع** مش هيوصلوك للسقف. إدخال Qdrant دلوقتي = خدمة زيادة + نسخ احتياطي زيادة + مصدر حقيقة تاني، **مقابل صفر مكسب**.

### 3.3 Memory

| المعيار | Mem0 | Redis | PostgreSQL + Summaries |
|---|---|---|---|
| مصمّم لإيه | ذاكرة محادثة عامة | Cache/جلسات سريعة | **حالة مجال منظّمة** |
| قابلية التدقيق | متوسطة | ضعيفة | ⭐ كاملة |
| Backup مع باقي البيانات | لا | لا | ⭐ نعم |
| مناسب لمجالك | ❌ | كـcache فقط | ✅ |

**التوصية: PostgreSQL + جدول `conversation_summaries`. تخطّى Mem0.**

السبب الجوهري: في مجالك **"الذاكرة" مش تفضيلات المستخدم — دي حالة المشروع**. إن `W7` كان مرفوض في دور 30 دي **حقيقة في قاعدة البيانات**، مش "ذكرى". خلط الاتنين بيخلق مصدرين حقيقة متعارضين — وده أسوأ نوع bug.

Redis يدخل لاحقاً كـ**cache + broker للطابور**، مش كذاكرة.

### 3.4 LlamaIndex vs LangChain

| المعيار | LlamaIndex | LangChain |
|---|---|---|
| التركيز | ⭐ الإدخال والاسترجاع | إطار عام واسع |
| التجريد | أخف | ثقيل |
| مناسب لـ | RAG محدّد | تركيبات معقّدة |

**التوصية: لا ده ولا ده كـframework أساسي.**

احتياجك الفعلي: (1) تقسيم مستندات، (2) embedding، (3) بحث. دول **~200 سطر Python**. استخدم `llama-index-core` لأدوات التقسيم بس لو عايز توفّر وقت، بس **متبنيش المعمارية جواه**.

السبب: إنت فريق **شخص واحد**. كل framework بتضيفه = ترقيات وbreaking changes وتصحيح أخطاء جوّه كود مش بتاعك. الكود اللي كتبته في التطبيق (صفر مكتبات شبكة) بيثبت إنك فاهم المبدأ ده — **طبّقه على السيرفر**.

---

## 4. تصميم نظام الوكلاء

### 4.1 المبدأ: قلّل الوكلاء، كتّر الأدوات

**قاعدة:** استخدم وكيل لما يكون فيه **قرار غير محدّد مسبقاً**. استخدم pipeline لما تكون الخطوات معروفة.

| العملية | وكيل ولا Pipeline؟ | ليه |
|---|---|---|
| استخراج رسمة | **Pipeline** | الخطوات ثابتة: صنّف → استخرج → تحقّق → خزّن |
| مقارنة Revisions | **Pipeline** | SQL diff — صفر قرارات |
| توليد تقرير يومي | **Pipeline + LLM كاتب** | البيانات محدّدة، الـLLM بيصيغ بس |
| **الإجابة على سؤال مفتوح** | **وكيل** ✅ | مش معروف مسبقاً أي بيانات محتاجة |
| **تحليل صورة موقع** | **وكيل** ✅ | محتاج يقرر يقارن بأنهي رسمة، ويسأل إيه |

### 4.2 التصميم المقترح: وكيل واحد + 3 عمّال

```
                    ┌──────────────────────┐
                    │  SITE ENGINEER AGENT │  ← الوحيد اللي "بيقرّر"
                    │  (LangGraph)         │
                    │  نموذج: Sonnet-class │
                    └──────────┬───────────┘
                               │ tools
        ┌──────────────┬───────┴───────┬──────────────┐
        ▼              ▼               ▼              ▼
┌──────────────┐ ┌───────────┐ ┌─────────────┐ ┌────────────┐
│ PROJECT      │ │ DOCUMENT  │ │  VISION     │ │ KNOWLEDGE  │
│ QUERY TOOLS  │ │ WORKER    │ │  WORKER     │ │ SEARCH     │
│              │ │           │ │             │ │            │
│ SQL حتمي     │ │ استخراج   │ │ تحليل صور   │ │ pgvector   │
│ · floor_state│ │ + validate│ │ + مقارنة    │ │ للنصوص     │
│ · compare    │ │ + diff    │ │   بالرسمة   │ │ غير المنظّمة│
│ · element    │ │           │ │             │ │            │
│ · wir_status │ │ async     │ │ نموذج vision│ │            │
│ · quantities │ │ queue     │ │ قوي         │ │            │
└──────────────┘ └───────────┘ └─────────────┘ └────────────┘
```

**مسؤوليات كل مكوّن:**

| المكوّن | المسؤولية | الأدوات | التواصل |
|---|---|---|---|
| **Site Engineer Agent** | يفهم السؤال، يختار الأدوات، يركّب الإجابة | كل اللي تحت | LangGraph state |
| **Project Query Tools** | إجابات حتمية من الـDB | SQL + domain logic | استدعاء مباشر (sync) |
| **Document Worker** | PDF/DWG → JSON منظّم + diff | Docling, OCR, LLM | **طابور async** (بطيء) |
| **Vision Worker** | تحليل صور، مقارنة بالرسمة | Vision LLM | طابور async |
| **Knowledge Search** | بحث دلالي في النصوص | pgvector | استدعاء مباشر |

**قاعدة التواصل الحاسمة:** العمّال **مابيتكلموش مع بعض**. كلهم بيكتبوا في PostgreSQL، والوكيل بيقرا منها. ده بيمنع أسوأ مشكلة في أنظمة multi-agent: **انتشار الأخطاء عبر الوكلاء من غير ما حد ياخد باله**.

---

## 5. تدفّق البيانات — من رفع الملف للنتيجة

### السيناريو: رفع Revision 05 لرسمة تسليح

```
[1] الموبايل: المستخدم يرفع Drawing-Rev05.pdf
     │  · التطبيق بيحسب SHA-256
     │  · بيرفع لـS3، وبيسجّل صف في documents (status=QUEUED)
     │  · ⚡ بيرجع فوراً للمستخدم — من غير انتظار
     ▼
[2] الطابور: Document Worker بياخد المهمة
     │
     ├─ 2a. تصنيف: إيه نوع المستند؟
     │      (drawing | BBS | WIR | spec | correspondence)
     │      → موديل صغير رخيص، أو قواعد على اسم الملف
     │
     ├─ 2b. استخراج المحتوى
     │      · PDF نصّي  → Docling (سريع، رخيص، دقيق)
     │      · PDF ممسوح → OCR (PaddleOCR/Surya) ثم Docling
     │      · صورة      → Vision مباشرة
     │
     ├─ 2c. الاستخراج المنظّم (LLM)
     │      Input:  نص/صور + JSON Schema صارم
     │      Output: { drawing_no, revision, date, discipline,
     │                elements: [ { mark, floor_from, floor_to,
     │                              thickness, vertical, horizontal,
     │                              edge, confidence } ] }
     │
     ├─ 2d. ⚠ التحقق من الـSchema  ← بوابة إجبارية
     │      · الحقول موجودة وأنواعها صح؟
     │      · فشل → RETRY بـprompt مصحّح (مرتين كحد أقصى)
     │
     ├─ 2e. ⚠ التحقق من المجال     ← البوابة الأهم
     │      · الأقطار من قائمة معروفة؟ (10,12,16,20,25,32,40)
     │      · التباعدات في مدى معقول؟ (75–400 مم)
     │      · أكواد الأدوار موجودة في المشروع؟
     │      · السُمك في مدى معقول؟ (200–1500 مم)
     │      · المدايات مش متداخلة ولا فيها فجوات؟
     │      → أي فشل = العنصر يتعلّم NEEDS_REVIEW، **مش** يتقبل بصمت
     │
     ├─ 2f. بوابة الثقة
     │      · عناصر عالية الثقة → تدخل تلقائي
     │      · منخفضة/فشلت التحقق → طابور مراجعة بشرية
     │
     ├─ 2g. التخزين والإصدار
     │      · INSERT في drawing_revisions
     │      · INSERT في element_schedules (بـrevision_id)
     │      · النسخة القديمة **مابتتمسحش أبداً**
     │
     └─ 2h. ⚡ المقارنة الحتمية  ← جوهر القيمة
            SELECT ... FROM element_schedules new
            FULL OUTER JOIN element_schedules old
              ON new.mark = old.mark AND new.floor = old.floor
            WHERE new.revision_id = :new AND old.revision_id = :old
              AND (new.vertical IS DISTINCT FROM old.vertical
                OR new.thickness IS DISTINCT FROM old.thickness
                OR ...)

            → W7 @ L35: vertical  8T25 → 10T25   [CHANGED]
            → W12 @ L35: (غير موجود) → موجود      [ADDED]
     │
     ▼
[3] تحليل الأثر (Impact) — حتمي كمان
     │  · العناصر اللي اتغيّرت، فيها إيه متصبوب بالفعل؟  → 🔴 حرج
     │  · فيها إيه WIR معتمد؟                            → 🔴 حرج
     │  · فيها إيه لسه ماتنفّذش؟                          → 🟢 معلومة
     │
     ▼
[4] الصياغة (LLM — أول مرة يتدخّل في العرض)
     │  Input: الفروق المحسوبة + تحليل الأثر
     │  Output: نص عربي مهني + تصنيف الخطورة
     │  ⚠ الـLLM **ممنوع** يغيّر أي رقم — بيصيغ بس
     │
     ▼
[5] الإشعار + المزامنة
        · Push للمهندسين المعنيين بالأدوار دي
        · التطبيق بيسحب الـdelta ويحدّث الكاش المحلي
        · شاشة الدور بتعرض بانر التغيير
```

**النقطة الحاسمة:** الخطوات 2h و3 — **قلب المنتج** — **صفر LLM**. حتمية، سريعة، مجانية، وقابلة للتدقيق. الـLLM بيتدخّل في 2c (ترجمة) و4 (صياغة) بس.

---

## 6. تصميم قاعدة البيانات

```sql
-- ═══════════════ تعدّد المستأجرين (الأساس المفقود حالياً) ═══════════
CREATE TABLE organizations (
    id              UUID PRIMARY KEY,
    name            TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE users (
    id              UUID PRIMARY KEY,
    org_id          UUID NOT NULL REFERENCES organizations(id),
    email           CITEXT UNIQUE NOT NULL,
    full_name       TEXT NOT NULL,
    role            TEXT NOT NULL,   -- ENGINEER|QC|MANAGER|VIEWER|ADMIN
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE projects (
    id              UUID PRIMARY KEY,
    org_id          UUID NOT NULL REFERENCES organizations(id),
    code            TEXT NOT NULL,          -- 'BHR-T1'
    name            TEXT NOT NULL,
    contractor      TEXT,
    consultant      TEXT,
    started_at      DATE,
    UNIQUE (org_id, code)
);

-- ═══════════════ هيكل المبنى ═══════════════
-- sort_index هو الحقيقة الترتيبية. الاسم للعرض بس.
-- ده اللي بيخلي '3M' (ميزانين) مايبوظش الترتيب.
CREATE TABLE floors (
    id              UUID PRIMARY KEY,
    project_id      UUID NOT NULL REFERENCES projects(id),
    code            TEXT NOT NULL,          -- 'B02','GROUND','3M','35'
    sort_index      INT  NOT NULL,          -- 0..N من تحت لفوق
    elevation_mm    INT,
    UNIQUE (project_id, code),
    UNIQUE (project_id, sort_index)
);

CREATE TABLE elements (
    id              UUID PRIMARY KEY,
    project_id      UUID NOT NULL REFERENCES projects(id),
    mark            TEXT NOT NULL,          -- 'T1-W7'
    element_type    TEXT NOT NULL,          -- WALL|COUPLING_BEAM|INTERNAL_BEAM|SLAB|COLUMN
    plan_x          NUMERIC, plan_y      NUMERIC,
    plan_w          NUMERIC, plan_h      NUMERIC,
    UNIQUE (project_id, mark)
);

-- ═══════════════ الرسومات والنسخ ═══════════════
CREATE TABLE drawings (
    id              UUID PRIMARY KEY,
    project_id      UUID NOT NULL REFERENCES projects(id),
    drawing_no      TEXT NOT NULL,
    title           TEXT,
    discipline      TEXT,                   -- ST|AR|ME|EL
    UNIQUE (project_id, drawing_no)
);

CREATE TABLE drawing_revisions (
    id              UUID PRIMARY KEY,
    drawing_id      UUID NOT NULL REFERENCES drawings(id),
    revision        TEXT NOT NULL,          -- '04','05','A'
    revision_date   DATE,
    file_key        TEXT NOT NULL,          -- مفتاح S3
    file_sha256     TEXT NOT NULL,          -- يمنع الرفع المكرّر
    status          TEXT NOT NULL,          -- QUEUED|EXTRACTING|NEEDS_REVIEW|ACTIVE|SUPERSEDED
    extracted_at    TIMESTAMPTZ,
    extraction_model TEXT,                  -- تتبّعية: أنهي موديل استخرج ده
    uploaded_by     UUID REFERENCES users(id),
    uploaded_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (drawing_id, revision)
);

-- ═══════════════ جداول التسليح (قلب النظام) ═══════════════
-- كل صف = مدى تسليح مربوط بنسخة معيّنة.
-- floor_to_exclusive بيرمّز قاعدة المجال صراحة في الـschema
-- بدل ما تبقى قاعدة شفهية بتتنسى.
CREATE TABLE element_schedules (
    id                  UUID PRIMARY KEY,
    project_id          UUID NOT NULL REFERENCES projects(id),
    element_id          UUID NOT NULL REFERENCES elements(id),
    revision_id         UUID NOT NULL REFERENCES drawing_revisions(id),

    floor_from_index    INT NOT NULL,
    floor_to_index      INT,                -- NULL = لحد آخر المبنى
    floor_to_exclusive  BOOLEAN NOT NULL,   -- TRUE للحوائط · FALSE للكمرات

    thickness_mm        INT,
    width_mm            INT,
    depth_mm            INT,
    rf_vertical         TEXT,               -- 'T25-100'
    rf_horizontal       TEXT,
    rf_edge             TEXT,
    rf_bottom           TEXT[],
    rf_top              TEXT[],
    rf_side             TEXT,
    rf_links            TEXT,

    confidence          NUMERIC(3,2),       -- 0.00–1.00 من الاستخراج
    needs_review        BOOLEAN NOT NULL DEFAULT FALSE,
    review_reason       TEXT,
    source_page         INT,
    source_bbox         JSONB               -- إحداثيات للرجوع للرسمة
);

CREATE INDEX ON element_schedules (project_id, element_id, revision_id);
CREATE INDEX ON element_schedules (revision_id) WHERE needs_review;

-- ═══════════════ التغييرات المكتشفة ═══════════════
CREATE TABLE schedule_changes (
    id              UUID PRIMARY KEY,
    project_id      UUID NOT NULL REFERENCES projects(id),
    element_id      UUID NOT NULL REFERENCES elements(id),
    from_revision   UUID REFERENCES drawing_revisions(id),
    to_revision     UUID NOT NULL REFERENCES drawing_revisions(id),
    floor_index     INT NOT NULL,
    change_type     TEXT NOT NULL,          -- CHANGED|ADDED|REMOVED
    field           TEXT,                   -- 'rf_vertical'
    old_value       TEXT,
    new_value       TEXT,
    impact          TEXT NOT NULL,          -- CRITICAL|HIGH|INFO
    impact_reason   TEXT,                   -- 'العنصر متصبوب بالفعل'
    acknowledged_by UUID REFERENCES users(id),
    acknowledged_at TIMESTAMPTZ,
    detected_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ═══════════════ الجودة والتنفيذ ═══════════════
CREATE TABLE wir (                          -- Work Inspection Requests
    id              UUID PRIMARY KEY,
    project_id      UUID NOT NULL REFERENCES projects(id),
    wir_no          TEXT NOT NULL,
    element_id      UUID REFERENCES elements(id),
    floor_index     INT NOT NULL,
    activity        TEXT NOT NULL,          -- REBAR|FORMWORK|CONCRETE
    status          TEXT NOT NULL,          -- DRAFT|SUBMITTED|APPROVED|REJECTED|CLOSED
    submitted_at    TIMESTAMPTZ,
    inspected_at    TIMESTAMPTZ,
    inspector       TEXT,
    remarks         TEXT,
    -- الرسمة اللي الفحص اتعمل عليها. ده اللي بيمكّن السؤال الحرج:
    -- "هل فيه حاجة اتفحصت على نسخة اتغيّرت بعد كده؟"
    against_revision UUID REFERENCES drawing_revisions(id),
    UNIQUE (project_id, wir_no)
);

CREATE TABLE photos (
    id              UUID PRIMARY KEY,
    project_id      UUID NOT NULL REFERENCES projects(id),
    element_id      UUID REFERENCES elements(id),
    floor_index     INT,
    file_key        TEXT NOT NULL,
    taken_at        TIMESTAMPTZ NOT NULL,
    taken_by        UUID REFERENCES users(id),
    gps_lat         NUMERIC, gps_lon NUMERIC,
    caption         TEXT,
    ai_analysis     JSONB,                  -- مخرجات Vision + الثقة
    ai_reviewed_by  UUID REFERENCES users(id)   -- NULL = لسه مش متأكّد بشرياً
);

CREATE TABLE issues (
    id              UUID PRIMARY KEY,
    project_id      UUID NOT NULL REFERENCES projects(id),
    element_id      UUID REFERENCES elements(id),
    floor_index     INT,
    severity        TEXT NOT NULL,          -- CRITICAL|MAJOR|MINOR|OBSERVATION
    status          TEXT NOT NULL,          -- OPEN|IN_PROGRESS|CLOSED
    title           TEXT NOT NULL,
    description     TEXT,
    source          TEXT NOT NULL,          -- HUMAN|AI_VISION|AI_DIFF
    confidence      NUMERIC(3,2),           -- للمصادر الآلية
    opened_by       UUID REFERENCES users(id),
    opened_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    closed_at       TIMESTAMPTZ
);

CREATE TABLE tasks (
    id              UUID PRIMARY KEY,
    project_id      UUID NOT NULL REFERENCES projects(id),
    floor_index     INT,
    title           TEXT NOT NULL,
    assigned_to     UUID REFERENCES users(id),
    due_date        DATE,
    done            BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ═══════════════ المعرفة النصّية (RAG) ═══════════════
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE doc_chunks (
    id              UUID PRIMARY KEY,
    project_id      UUID NOT NULL REFERENCES projects(id),
    document_id     UUID NOT NULL,
    chunk_index     INT NOT NULL,
    content         TEXT NOT NULL,
    -- الميتاداتا دي بتخلي الفلترة قبل البحث ممكنة،
    -- وده اللي بيرفع الدقة أكتر من أي تحسين في الـembedding
    floor_index     INT,
    element_marks   TEXT[],
    doc_type        TEXT,
    page            INT,
    embedding       vector(1024)
);

CREATE INDEX ON doc_chunks USING hnsw (embedding vector_cosine_ops);
CREATE INDEX ON doc_chunks (project_id, floor_index);
CREATE INDEX ON doc_chunks USING gin (element_marks);

-- ═══════════════ التدقيق (مطلب تعاقدي) ═══════════════
CREATE TABLE audit_log (
    id              BIGSERIAL PRIMARY KEY,
    project_id      UUID NOT NULL,
    actor_type      TEXT NOT NULL,          -- USER|AGENT|SYSTEM
    actor_id        UUID,
    action          TEXT NOT NULL,
    entity_type     TEXT NOT NULL,
    entity_id       UUID,
    before          JSONB,
    after           JSONB,
    at              TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

**ملاحظات تصميمية مهمة:**

| القرار | التبرير |
|---|---|
| `floor_to_exclusive` كعمود | قاعدة الحائط/الكمرة **مرمّزة في البيانات**، مش في كود بيتنسى. أي استعلام لازم يحترمها |
| `sort_index` مصدر الترتيب | الأسماء مش قابلة للترتيب (`3M` بين `03` و`04`) |
| `file_sha256` | يمنع تحليل نفس الملف مرتين — توفير مباشر في التكلفة |
| `against_revision` في WIR | يمكّن أخطر سؤال: **"إيه اللي اتفحص على نسخة قديمة؟"** |
| `ai_reviewed_by` nullable | يفرّق بين "الـAI قال" و"مهندس أكّد" |
| `extraction_model` | لما موديل يطلع غلط، تعرف أنهي بيانات تعيد استخراجها |
| Audit شامل الوكيل | "مين غيّر ده؟" لازم تجاوب حتى لو الجواب "الوكيل" |

---

## 7. نظام RAG

### 7.1 قرار النطاق — الأهم في القسم ده

| نوع المستند | المسار | ليه |
|---|---|---|
| Reinforcement Schedules | ❌ **SQL** | منظّم — RAG هيقلّل الدقة |
| BBS / Quantities | ❌ **SQL** | منظّم |
| WIR (بيانات) | ❌ **SQL** | منظّم |
| **Specifications** | ✅ **RAG** | نثر طويل غير منظّم |
| **Method Statements** | ✅ **RAG** | نثر |
| **ITP / معايير** | ✅ **RAG** | نثر مرجعي |
| **مراسلات / محاضر** | ✅ **RAG** | نثر |
| ملاحظات المهندسين | ✅ **RAG** | نص حر |

### 7.2 التقسيم (Chunking)

```python
# التقسيم حسب البنية، مش حسب عدد الحروف.
# القطع اللي بتقطع في نص البند بتخرّب الاسترجاع.

CHUNK_TARGET = 800   # توكن
CHUNK_OVERLAP = 120  # للحفاظ على السياق عبر الحدود

# 1) Docling بيدّي بنية المستند (عناوين، أقسام، جداول)
# 2) نقسّم عند حدود الأقسام
# 3) الأقسام الأكبر من الهدف تتقسّم عند حدود الجُمل
# 4) الجداول تتحوّل Markdown و**متتقسّمش أبداً**
# 5) كل chunk بياخد "ترويسة سياق" — بتحسّن الاسترجاع بشكل ملحوظ:
#
#    [مشروع: BHR Tower 1 | مستند: Method Statement MS-014
#     | قسم: 4.3 صبّ الخرسانة | دور: 35]
#    <المحتوى>
```

### 7.3 الـEmbedding

| البند | التوصية | التبرير |
|---|---|---|
| النموذج | متعدد اللغات، بُعد 1024 | **إجباري** — مستنداتك عربي/إنجليزي مختلط |
| مكان التشغيل | على السيرفر (CPU كافٍ) | 40 ألف chunk = دقايق. مفيش داعي لـGPU |
| التخزين | pgvector + HNSW | جوّه نفس الـDB |
| إعادة البناء | عند تغيير النموذج فقط | خزّن اسم النموذج مع الـchunk |

### 7.4 البحث — هجين إجباري

```python
# البحث الدلالي لوحده بيفشل على أكواد العناصر.
# "T1-W7" قريب دلالياً من "T1-W17" و"T1-W4" — كارثة في مجالنا.
# لازم بحث نصّي دقيق جنبه.

async def search(project_id, query, floor=None, marks=None, k=8):
    # 1) استخراج الكيانات من السؤال بـregex (مش LLM — أسرع وأدق)
    marks = marks or extract_marks(query)      # T1-W7, W12
    floor = floor or extract_floor(query)      # "دور 35" → 35

    # 2) فلترة صارمة قبل البحث ← أكبر مكسب دقة
    filters = {"project_id": project_id}
    if floor is not None: filters["floor_index"] = floor
    if marks:             filters["element_marks"] = marks

    # 3) مسارين متوازيين
    dense  = await vector_search(query, filters, k=20)   # المعنى
    sparse = await fulltext_search(query, filters, k=20) # الأكواد الدقيقة

    # 4) دمج بـReciprocal Rank Fusion
    fused = rrf(dense, sparse)

    # 5) إعادة ترتيب (اختياري، يضيف ~200ms)
    return await rerank(query, fused)[:k]
```

### 7.5 الربط بعناصر المشروع

كل chunk بيتربط بالكيانات وقت الإدخال:

```python
def link_chunk(chunk, project):
    return {
        # regex على أنماط الأكواد المعروفة في المشروع
        "element_marks": find_marks(chunk.text, project.known_marks),
        "floor_index":   find_floor(chunk.text, project.floor_codes),
        "doc_type":      chunk.document.doc_type,
    }
```

ده بيمكّن السؤال المركّب: *"إيه الـmethod statement بتاع صبّ الحائط W7 في دور 35؟"* → فلترة بالدور والكود **قبل** البحث الدلالي.

---

## 8. نظام Vision AI

### 8.1 تقييم صادق للقدرات

**ده أضعف مكوّن في المنظومة كلها. لازم تتعامل معاه على الأساس ده.**

| المهمة | الموثوقية | الحكم |
|---|---|---|
| قراءة Title Block من رسمة | 🟢 عالية | استخدمه |
| قراءة جدول مطبوع من رسمة | 🟢 عالية | استخدمه مع تحقق |
| تصنيف "شدّة / حديد / صبّة" | 🟢 عالية | استخدمه |
| اكتشاف مشاكل واضحة (صدأ، مياه، ركام) | 🟡 متوسطة | مساعد بشري |
| التحقق من تباعد الأسياخ من صورة | 🟠 ضعيفة | يحتاج مرجع قياس |
| **عدّ الأسياخ في صورة موقع** | 🔴 **غير موثوقة** | **متبنيش عليها** |
| قياس الغطاء الخرساني | 🔴 غير موثوقة | يحتاج أداة قياس |

**ليه العدّ بيفشل؟** أسياخ متداخلة، زوايا مايلة، إضاءة متغيّرة، أجزاء محجوبة، وتشابه بصري بين قطر 25 و32. النماذج بترجّع رقم **بثقة عالية** وهو غلط — وده **أخطر** من إنها ترفض.

### 8.2 المسار الموصى به

```
صورة → [1] تصنيف السياق (رخيص)
              ↓
       [2] استخراج الملاحظات الوصفية (مش قياسات)
              ↓
       [3] تقاطع مع بيانات الرسمة من الـDB
              ↓
       [4] توليد أسئلة تحقّق للمهندس — مش أحكام
              ↓
       [5] المهندس يؤكّد أو يرفض
              ↓
       [6] التخزين مع علامة "مؤكّد بشرياً"
```

**مثال على الفرق:**

| ❌ الخطأ | ✅ الصح |
|---|---|
| "عدد الأسياخ 10 مطابق للرسمة ✓" | "الرسمة بتقول 10T25 للحائط ده. من الصورة الرأسي شكله متقارب. **من فضلك أكّد العدد**" |

الأول بيدّي **إذن ضمني للصبّ** بناءً على قدرة النموذج مايملكهاش. التاني بيوجّه انتباه المهندس للمكان الصح **من غير ما يدّعي**.

### 8.3 تقليل الأخطاء

| الأسلوب | الأثر |
|---|---|
| **حقن سياق الرسمة في الـprompt** | ⭐ الأعلى — النموذج بيتحقق مش بيخمّن |
| **حظر مخرجات رقمية للقياسات** | ⭐ عالي — يمنع أخطر نوع خطأ |
| **صور متعدّدة للعنصر الواحد** | عالي |
| **طلب مرجع قياس في الصورة** | عالي |
| **صياغة النتيجة كسؤال مش حكم** | ⭐ عالي |
| **مجموعة اختبار مسمّاة بشرياً (200+ صورة)** | ⭐ إجباري للقياس |

> ⚠️ **تحذير عن Confidence Score:** درجات الثقة اللي بترجع من الـLLM **مش احتمالات معايرة**. النموذج ممكن يقول 0.95 وهو غلط. استخدمها للترتيب فقط، **متبنيش عليها قرار**.

---

## 9. البنية التحتية

### 9.1 المواصفات

| المرحلة | المواصفات | الخدمات | التكلفة التقديرية/شهر |
|---|---|---|---|
| **Dev / MVP** | 4 vCPU · 8GB RAM · 80GB | كله في Docker Compose | ~$20–45 |
| **إنتاج صغير** (1–3 مشاريع، <30 مستخدم) | 8 vCPU · 16GB · 240GB NVMe | App + DB + Worker | ~$45–140 |
| **إنتاج متوسط** (10+ مشاريع) | 8 vCPU + DB منفصل 4 vCPU/16GB | مفصولة | ~$150–300 |

> ⚠️ **تنبيه تسعير:** أسعار VPS شهدت تغيّرات كبيرة خلال 2026 (Hetzner رفعت أسعار CPX/CCX). **تحقّق من السعر الحالي وقت التنفيذ** — الأرقام دي للتخطيط بس.

**مفيش GPU مطلوب.** الـembedding على CPU كافٍ لحجمك، والـLLM/Vision عبر API.

### 9.2 Docker Architecture

```yaml
services:
  api:            # FastAPI — طلبات متزامنة
    depends_on: [postgres, redis]
    deploy: { replicas: 2 }

  worker:         # ARQ/Celery — استخراج، vision، embedding
    depends_on: [postgres, redis]
    deploy: { replicas: 2 }
    # منفصل عن api عشان استخراج بيستغرق دقايق
    # مايعطّلش طلبات المستخدم

  postgres:       # 16 + pgvector — مصدر الحقيقة الوحيد
    volumes: [pgdata:/var/lib/postgresql/data]

  redis:          # طابور + cache (مش ذاكرة)

  minio:          # تخزين الملفات المتوافق مع S3

  caddy:          # عكسي + TLS تلقائي

volumes: { pgdata:, miniodata: }
```

**قرار معماري:** فصل `api` عن `worker` **مش اختياري**. استخراج رسمة ممكن ياخد 2–5 دقايق. لو في نفس العملية، المستخدم هيستنى أو الطلب هيـtimeout.

### 9.3 الأمان

| الطبقة | الإجراء | الأولوية |
|---|---|---|
| النقل | TLS 1.3 إجباري | 🔴 |
| المصادقة | JWT قصير + refresh دوّار | 🔴 |
| العزل | **Row-Level Security على `project_id`** | 🔴 |
| مفاتيح الـLLM | على السيرفر فقط — **تتشال من الموبايل** | 🔴 |
| الملفات | Presigned URLs منتهية الصلاحية | 🟠 |
| السكون | تشفير القرص + نسخ مشفّرة | 🟠 |
| التدقيق | كل تغيير مسجّل بالفاعل | 🔴 (تعاقدي) |
| الاحتفاظ | حذف بيانات العميل عند الطلب | 🟠 (تعاقدي) |
| المزوّد | **اتفاقية عدم تدريب على بياناتك** | 🔴 |

> ⚠️ **النقطة القانونية الأهم:** رسومات المشروع **ملك للعميل/الاستشاري**، مش لك. إرسالها لمزوّد LLM بيحتاج **إذن تعاقدي صريح**. راجع ده **قبل** ما تكتب سطر كود سيرفر — دي ممكن تقتل المنتج كله.

---

## 10. التكلفة

### 10.1 أسعار الـLLM (أغسطس 2026)

| النموذج | إدخال /1M | إخراج /1M | الاستخدام المقترح |
|---|---|---|---|
| Claude Sonnet 5 | $2 (يرتفع لـ$3 بعد 31 أغسطس) | $10 → $15 | الوكيل + الاستخراج الدقيق |
| GPT-5.2 | $1.75 | $14.00 | بديل |
| Gemini 3.1 Pro | $2.00 | $12.00 | بديل vision |
| **Gemini 3 Flash** | **$0.50** | **$3.00** | التصنيف + المهام الرخيصة |

> ملاحظة: Claude 4.7+ بيستخدم tokenizer بينتج **~30% توكنز أكتر** لنفس النص — احسبها في المقارنة.

### 10.2 تكلفة التشغيل الشهرية

**افتراض:** مشروع واحد، 10 مهندسين، 40 مستند/شهر، 300 سؤال/شهر، 200 صورة/شهر.

| البند | الحساب | التكلفة |
|---|---|---|
| VPS | خادم واحد | $45–140 |
| PostgreSQL + pgvector | ضمن الخادم | $0 |
| MinIO | ضمن الخادم | $0 |
| Embeddings | ~1M توكن | ~$0.10 |
| **استخراج المستندات** | 40 × ~30k توكن | ~$3–6 |
| **أسئلة الوكيل** | 300 × ~15k (3 جولات) | ~$12–20 |
| **Vision** | 200 صورة | ~$8–15 |
| التقارير | 30 × 8k | ~$2 |
| نسخ احتياطي خارجي | | $5 |
| **الإجمالي** | | **≈ $75–190/شهر** |

**التكلفة لكل مشروع تقل بشدة مع التوسّع** — الخادم ثابت، والـAPI بس بيزيد. 10 مشاريع ≈ $400–600/شهر، يعني **$40–60/مشروع**.

### 10.3 تكلفة البداية

| البند | التكلفة |
|---|---|
| التطوير (فريق شخص واحد) | **المورد النادر — 4–6 شهور** |
| بنية تحتية أثناء التطوير | ~$150 إجمالي |
| رصيد API للتجارب | ~$100–200 |
| دومين + شهادات | ~$30/سنة |
| **الإجمالي النقدي** | **< $400** |

**الخلاصة: المشروع مش محدود بالمال — محدود بالوقت.** وده بيقوّي التوصية بتقليل المكوّنات.

### 10.4 إيه اللي ينفع مجاناً؟

| المكوّن | مجاني؟ |
|---|---|
| PostgreSQL + pgvector | ✅ كلياً |
| MinIO / Qdrant / Chroma | ✅ Apache-2.0 self-hosted |
| Docling / PaddleOCR / Surya | ✅ |
| نماذج Embedding | ✅ محلياً على CPU |
| LangGraph / FastAPI | ✅ |
| **LLM للاستخراج والإجابة** | ❌ **ده بند التكلفة الوحيد فعلياً** |
| **Vision** | ❌ |

**متى تحتاج APIs مدفوعة؟** من اليوم الأول للاستخراج. **موديلات مفتوحة محلياً غير عملية** لأن الاستخراج المنظّم من رسومات هندسية بيحتاج قدرة عالية، وتشغيلها محلياً يحتاج GPU بتكلفة أعلى من الـAPI في حجمك.

---

## 11. خطة التنفيذ

### Phase 1 — الأساس (6–8 أسابيع) 🔴 حرج

**الهدف: تعدّد المشاريع والمستخدمين. مفيش AI جديد.**

| المهمة | المخرج |
|---|---|
| FastAPI + PostgreSQL + Docker | سيرفر شغّال |
| الـSchema كامل + RLS | عزل المستأجرين |
| Auth + أدوار | تسجيل دخول |
| **نقل جدول المشروع من `assets/` للـDB** | ⭐ **يفكّ القفل على مشروع واحد** |
| مزامنة delta + حل التعارضات | كل موبايل مش جزيرة |
| **نقل مفتاح الـLLM للسيرفر** | تحكّم في التكلفة |
| نقل `ScheduleLogic` للسيرفر (Python) | مصدر حقيقة واحد للمنطق |

> **ليه ده أولاً؟** من غيره **مفيش منتج** — مجرد أداة لمشروع واحد. وده أكبر عائق حالي، مش الـAI.

### Phase 2 — خط الاستخراج والمقارنة (6–8 أسابيع) 🔴 القيمة الأساسية

| المهمة | المخرج |
|---|---|
| طابور الإدخال + Docling + OCR | معالجة غير متزامنة |
| استخراج منظّم بـschema صارم | PDF → JSON |
| **بوابات التحقق (schema + مجال)** | ⭐ منع الهلوسة |
| نموذج النسخ (Revisions) | تأريخ كامل |
| **محرك المقارنة الحتمي** | ⭐ `W7: 8T25 → 10T25` |
| تحليل الأثر + الإشعارات | التنبيه الاستباقي |
| طابور المراجعة البشرية | الثقة المنخفضة تتراجع |

> **ده بالظبط السيناريو اللي طلبته.** ولاحظ: **صفر RAG وصفر multi-agent**.

### Phase 3 — المساعد (4–6 أسابيع)

| المهمة | المخرج |
|---|---|
| نقل الوكيل الحالي للسيرفر | أدوات أقوى |
| RAG للمستندات النثرية | Specs/Method Statements |
| بحث هجين | دقة على الأكواد |
| Dashboard تلقائي للدور | السيناريو 2 |
| تاريخ العنصر الكامل | السيناريو 3 |
| التقرير اليومي | السيناريو 5 |
| **خط تقييم (evals)** | ⭐ قياس التحسّن |

### Phase 4 — Vision (6–8 أسابيع)

| المهمة | المخرج |
|---|---|
| تصنيف الصور | تنظيم تلقائي |
| ربط الصورة بالعنصر والدور | سياق |
| تحقّق موجّه بالرسمة | السيناريو 4 |
| مجموعة اختبار 200+ صورة | قياس الدقة |
| سير تأكيد المهندس | مسؤولية واضحة |

### Phase 5 — الاستقلالية (بعد إثبات 1–4)

مراقبة استباقية، اكتشاف تعارضات، توقّع مخاطر، تكامل مع أنظمة المقاولين.

> ⚠️ **لا تبدأ Phase 5 قبل ما تثبت دقة 1–4 على مشروع حقيقي لمدة 3 شهور.**

---

## 12. المخاطر

| # | الخطر | الاحتمال | الأثر | التخفيف |
|---|---|---|---|---|
| 1 | **هلوسة في استخراج التسليح** | 🔴 عالي | 🔴 كارثي | schema + تحقق مجال + بوابة ثقة + مراجعة بشرية. **ممنوع دخول تلقائي للثقة المنخفضة** |
| 2 | **Vision بيدّي تأكيد كاذب** | 🔴 عالي | 🔴 كارثي | حظر المخرجات الرقمية للقياسات؛ صياغة كسؤال؛ تأكيد إجباري |
| 3 | **مسؤولية قانونية** | 🟠 متوسط | 🔴 كارثي | **إخلاء مسؤولية صريح**: أداة مساعدة مش بديل عن المهندس. الأختام تفضل بشرية |
| 4 | **سرّية بيانات العميل** | 🟠 متوسط | 🔴 كارثي | إذن تعاقدي، عدم تدريب، تشفير، حذف عند الطلب |
| 5 | **الاعتماد الزائد (deskilling)** | 🟠 متوسط | 🟠 عالي | إظهار **المصدر دايماً**؛ إظهار عدم اليقين؛ متخفيش الفجوات |
| 6 | **رسومات ممسوحة رديئة** | 🔴 عالي | 🟠 عالي | OCR + كشف الجودة + رفض واضح بدل تخمين |
| 7 | **الشبكة في الموقع** | 🔴 عالي | 🟠 عالي | **أوفلاين-أول إجباري** (التطبيق عامل ده صح بالفعل) |
| 8 | **انفجار التكلفة** | 🟡 منخفض | 🟠 عالي | مفاتيح على السيرفر + حدود لكل مستأجر + cache على SHA |
| 9 | **DWG مش مقروء** | 🔴 عالي | 🟡 متوسط | مسار DXF/PDF + توثيق واضح |
| 10 | **تعارض مزامنة** | 🟠 متوسط | 🟡 متوسط | ملكية لكل حقل + آخر-كاتب-يفوز مع سجل |

### الخطر الأكبر — بصراحة

> **إن المهندس يثق في مخرَج غلط ويصبّ عليه.**

كل قرار معماري في الوثيقة دي بيخدم تقليل الخطر ده. الأمان مش في دقة النموذج — الأمان في **بنية بتمنع المخرَج غير المتحقّق منه إنه يوصل لقرار تنفيذي**.

---

## 13. الرأي النهائي

### هل المشروع قابل للتحوّل لمنتج تجاري؟

**نعم — بشروط.**

### الميزة التنافسية الحقيقية

**مش الـAI.** كل منافس هيضيف LLM خلال سنة. الميزة هي **نموذج المجال العميق** اللي اتبنى بالفعل:

| الميزة | Procore / Autodesk Build / Fieldwire | CoreWall |
|---|---|---|
| إدارة مستندات عامة | ⭐ ناضجة | أقل |
| سير عمل RFI/Submittal | ⭐ ناضجة | غير موجود |
| **فهم جداول التسليح** | ❌ **غير موجود** | ⭐ **مبني** |
| **دلالات المدايات عبر الأدوار** | ❌ | ⭐ |
| **اكتشاف فجوات الجدول** | ❌ | ⭐ |
| **مقارنة التسليح بين الأدوار** | ❌ | ⭐ |
| **عدّ الموقع مقابل الرسمة** | ❌ | ⭐ |
| واجهة عربية أصلية | ضعيفة | ⭐ |

**التموضع:** مش "Procore أرخص". دي **أداة متخصّصة في تسليح الأبراج** — تعمل الحاجة الواحدة اللي بتوجع فعلاً وبعمق مافيش حد بيعمله.

### الحقائق الصعبة

1. **العائق مش الـAI — هو Multi-tenancy.** التطبيق مايقدرش يفتح مشروع تاني. ده يوم-صفر.
2. **مشروع واحد مش تحقّق (validation).** لازم 3 مشاريع مختلفة قبل أي ادّعاء عام.
3. **دورة البيع في الإنشاءات طويلة** — 6–18 شهر للمقاولين الكبار.
4. **الاستخراج لازم يوصل >95% دقة** أو المهندسين هيبطّلوا يستخدموه. 85% أسوأ من صفر لأنه بيخلق شك.
5. **فريق شخص واحد مخاطرة تجارية** للعملاء المؤسسيين.

### توصيتي

| # | التوصية |
|---|---|
| 1 | **ابنِ Phase 1 و2 بس.** المقارنة الآلية للنسخ لوحدها منتج قابل للبيع |
| 2 | **أجّل Qdrant وMem0 وMulti-agent** لحد ما يكون فيه سبب قياسي |
| 3 | **استثمر في التحقق أكتر من النماذج.** بوابات التحقق هي المنتج |
| 4 | **تحقّق على 3 مشاريع** قبل التسويق |
| 5 | **احسم الوضع القانوني للبيانات** قبل كتابة كود السيرفر |
| 6 | **حافظ على المبدأ الحاكم**: التطبيق بيحسب، الـAI بيفسّر. ده أفضل قرار في المشروع كله |

### التقييم النهائي

| المحور | التقييم |
|---|---|
| الجدوى التقنية | ⭐⭐⭐⭐⭐ |
| قوة نموذج المجال | ⭐⭐⭐⭐⭐ |
| جودة الكود الحالي | ⭐⭐⭐⭐ |
| جاهزية المنتج | ⭐⭐ (multi-tenancy مفقود) |
| الفرصة السوقية | ⭐⭐⭐⭐ (متخصّصة بس حقيقية) |
| مخاطر التنفيذ | ⭐⭐⭐ (متوسطة — الدقة والقانوني) |

**الحكم: امضِ — بنطاق أضيق من المقترح.**

الأصل الحقيقي في CoreWall مش طبقة الـAI، ده **الفهم المرمّز لكيفية عمل تسليح الأبراج**. طبقة الـAI بتضاعف قيمة الأصل ده. لا تضحِّ بوضوح الأصل مقابل تعقيد معماري لسه مالوش مبرّر.

---

## ملحق: قرارات معمارية في سطر واحد

| القرار | الاختيار | السبب في جملة |
|---|---|---|
| Orchestration | LangGraph (Phase 3+) | Checkpointing وHITL — بس مش قبل ما تحتاجهم |
| Vector DB | pgvector → Qdrant لاحقاً | 40 ألف vector مايستاهلوش خدمة منفصلة |
| Memory | PostgreSQL | ذاكرتك حالة مشروع، مش تفضيلات |
| RAG Framework | كود مباشر | فريق شخص واحد، تبعيات أقل |
| عدد الوكلاء | **واحد** + عمّال حتميين | Multi-agent بيضيف فشل بلا مكسب |
| مقارنة النسخ | **SQL حتمي** | ⭐ RAG الأداة الغلط تماماً هنا |
| Vision | مساعد بشري فقط | العدّ غير موثوق — والثقة غير معايرة |
| الاستخراج | LLM + بوابات تحقق | التحقق أهم من النموذج |
| المفاتيح | السيرفر فقط | تحكّم في التكلفة والأمان |
| أوفلاين | إجباري | الموقع مالوش شبكة |

---

*انتهت الدراسة · أغسطس 2026*
