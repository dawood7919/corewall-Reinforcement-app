#!/usr/bin/env python3
"""
فحوصات نصّية سريعة قبل البناء.

كلها من نوع واحد: غلطة ثمنها في الكومبايلر خمس دقايق، وفي سكربت تانيتين.
كل واحدة هنا وقّعت بناء فعلاً قبل كده — مش احتياطات نظرية.

١. لامدا لاحقة على دالة آخر بارامتر فيها مش دالة.

    fun CwButton(label: String, onClick: () -> Unit, ..., fillWidth: Boolean = false)

    CwButton("حفظ") { save() }     // ← مابيكومبايلش

اللامدا اللاحقة في كوتلن بتروح لآخر بارامتر، مش لأول بارامتر من نوع دالة.
لما آخر بارامتر يبقى `Boolean` الكلام ده خطأ compile — بس الخطأ ده كلّفنا
بناءين كاملين (خمس دقايق للواحد) عشان بيظهر في آخر السجل بعد آلاف السطور.
الفحص ده بيجري في تانيتين قبل ما البناء يبدأ.

بيفحص بس الدوال المعرّفة في المشروع نفسه (اسمها بيبدأ بحرف كبير ومعرّفة
مرة واحدة، عشان مانتلغبطش في التحميل الزائد).

٢. `when` على enum ناقصها قيمة ومفيهاش `else`.

    when (config.provider) {
        AiProviderId.OPENAI -> …
        // ← قيمة جديدة اتضافت للـenum ومحدش زوّدها هنا
    }

بيحصل دايماً بعد ما تتضاف قيمة جديدة: الكومبايلر بيمسكها، بس بعد خمس
دقايق وفي ملف واحد في المرة.

٣. كيان مسجّل في `@Database` ومالوش `@Entity`.

    @Entity(tableName = "doc_facts")
    /** … توثيق حاجة تانية اندسّ هنا … */
    @Entity(tableName = "doc_chunks")
    data class DocChunkEntity(…)        // ← خد الاتنين
    data class DocFactEntity(…)         // ← فضل من غير تعليق

بيحصل لما تضاف كتلة قبل `data class` من غير حساب التعليقات اللي فوقه.
الكومبايلر بيمسكها، بس بعد خمس دقايق.

٤. استيراد متكرر لنفس الاسم في نفس الملف.

    import com.corewall.qaqc.ui.design.Radius
    ...
    import com.corewall.qaqc.ui.design.Radius   // ← Conflicting import

كوتلن بيعتبر ده تعارض مش تكرار، وبيوقّع الملف كله. سهل جداً يحصل لما
تتضاف استيرادات في أكتر من مكان في نفس الملف.
"""
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
SRC = REPO / "app" / "src"

DECL = re.compile(r"^(?:private |internal |public )?fun\s+([A-Z]\w*)\s*\(", re.M)
LINE_COMMENT = re.compile(r"//[^\n]*")
BLOCK_COMMENT = re.compile(r"/\*.*?\*/", re.S)
STRING = re.compile(r'"""(?:.|\n)*?"""|"(?:\\.|[^"\\\n])*"')


def strip_noise(text: str) -> str:
    """بيشيل التعليقات والنصوص وبيسيب نفس عدد الحروف عشان المواضع تفضل صح."""
    def blank(m):
        return "".join(" " if ch != "\n" else "\n" for ch in m.group(0))
    text = BLOCK_COMMENT.sub(blank, text)
    text = STRING.sub(blank, text)
    return LINE_COMMENT.sub(blank, text)


def match_paren(text: str, open_idx: int) -> int:
    """موضع القوس المقابل، أو -1."""
    depth = 0
    for i in range(open_idx, len(text)):
        if text[i] == "(":
            depth += 1
        elif text[i] == ")":
            depth -= 1
            if depth == 0:
                return i
    return -1


def split_top_level(params: str) -> list[str]:
    parts, depth, cur = [], 0, []
    prev = ""
    for ch in params:
        if ch in "(<[":
            depth += 1
        # `>` في `->` سهم مش قفلة قوس. من غير الشرط ده كل نوع دالة
        # بيخلّي العمق سالب وباقي البارامترات مابتتقسمش.
        elif ch in ")]" or (ch == ">" and prev != "-"):
            depth -= 1
        prev = ch
        if ch == "," and depth == 0:
            parts.append("".join(cur))
            cur = []
        else:
            cur.append(ch)
    if "".join(cur).strip():
        parts.append("".join(cur))
    return [p.strip() for p in parts if p.strip()]


def is_function_type(param: str) -> bool:
    """آخر بارامتر — نوعه دالة ولا لأ؟ (بعد ما نشيل القيمة الافتراضية)."""
    body = param
    depth = 0
    for i, ch in enumerate(body):
        if ch in "(<[":
            depth += 1
        elif ch in ")]" or (ch == ">" and body[i - 1:i] != "-"):
            depth -= 1
        elif ch == "=" and depth == 0 and body[i:i + 2] != "==":
            body = body[:i]
            break
    if ":" not in body:
        return False
    type_text = body.split(":", 1)[1]
    # `->` على العمق الأعلى، أو جوّه قوس واحد (نوع دالة قابل للـnull).
    depth = 0
    for i, ch in enumerate(type_text):
        if ch in "(<[":
            depth += 1
        elif ch in ")]" or (ch == ">" and type_text[i - 1:i] != "-"):
            depth -= 1
        elif ch == "-" and type_text[i:i + 2] == "->" and depth <= 1:
            return True
    return False


IMPORT = re.compile(r"^import\s+(\S+)\s*$", re.M)

ENUM_DECL = re.compile(r"^enum class (\w+)\s*(?:\([^)]*\))?\s*\{", re.M)
WHEN_START = re.compile(r"\bwhen\s*\([^)]*\)\s*\{")


def enum_values(sources: dict) -> dict:
    """اسم الـenum → قيَمه. بيقرا القيَم لحد أول `;` أو نهاية الجسم."""
    out: dict = {}
    for text in sources.values():
        for m in ENUM_DECL.finditer(text):
            body_start = m.end()
            depth, i = 1, body_start
            while i < len(text) and depth > 0:
                if text[i] == "{":
                    depth += 1
                elif text[i] == "}":
                    depth -= 1
                i += 1
            body = text[body_start:i - 1]
            body = body.split(";", 1)[0]
            names = re.findall(r"(?:^|,)\s*([A-Z][A-Z0-9_]*)\s*(?=[,(\n{]|$)", body)
            if names:
                out[m.group(1)] = set(names)
    return out


def non_exhaustive_whens(sources: dict, enums: dict) -> list:
    """`when` بتقارن قيَم enum من غير `else` وناقصها قيمة."""
    problems = []
    for f, text in sources.items():
        for m in WHEN_START.finditer(text):
            depth, i = 1, m.end()
            while i < len(text) and depth > 0:
                if text[i] == "{":
                    depth += 1
                elif text[i] == "}":
                    depth -= 1
                i += 1
            # `when` كـ**جملة** مش لازمة تكون شاملة (تحذير مش خطأ)،
            # وكـ**تعبير** لازمة. الفرق إن التعبير بيتسند لحاجة: بعد
            # `=` أو `return` أو كوسيط. من غير التفرقة دي الفحص بيطلّع
            # نتايج خاطئة على كود شغّال — وفحص بيكدب بيتلغى بعد يومين.
            head = text.rfind("\n", 0, m.start())
            before = text[head + 1:m.start()].rstrip()
            if not before.endswith(("=", "return", "(", ",")):
                continue

            body = text[m.end():i - 1]
            if re.search(r"(^|\n)\s*else\s*->", body):
                continue
            used = re.findall(r"\b([A-Z]\w*)\.([A-Z][A-Z0-9_]*)\b", body)
            by_enum: dict = {}
            for enum, value in used:
                if enum in enums and value in enums[enum]:
                    by_enum.setdefault(enum, set()).add(value)
            for enum, seen in by_enum.items():
                # قيمة واحدة مش `when` شامل — غالباً مقارنة عادية.
                if len(seen) < 2:
                    continue
                missing = enums[enum] - seen
                if missing:
                    line = text.count("\n", 0, m.start()) + 1
                    problems.append(
                        f"{f.relative_to(REPO)}:{line}: `when` على {enum} "
                        f"ناقصها {', '.join(sorted(missing))} ومفيهاش else."
                    )
    return problems


def duplicate_imports(files: list[Path]) -> list[str]:
    """نفس الاسم متستورد مرتين في نفس الملف."""
    problems = []
    for f in files:
        seen: dict[str, int] = {}
        for m in IMPORT.finditer(f.read_text(encoding="utf-8")):
            name = m.group(1)
            line = f.read_text(encoding="utf-8").count("\n", 0, m.start()) + 1
            if name in seen:
                problems.append(
                    f"{f.relative_to(REPO)}:{line}: استيراد متكرر «{name}» "
                    f"(اتستورد قبل كده في سطر {seen[name]}) — كوتلن بيعتبره تعارض."
                )
            else:
                seen[name] = line
    return problems


DB_ENTITIES = re.compile(r"entities\s*=\s*\[(.*?)\]", re.S)


def orphan_entities(sources: dict) -> list:
    """كل كلاس مسجّل في `@Database` لازم يكون عليه `@Entity` مباشرة."""
    registered = set()
    for text in sources.values():
        for m in DB_ENTITIES.finditer(text):
            registered |= set(re.findall(r"(\w+)::class", m.group(1)))
    if not registered:
        return []

    problems = []
    for f, text in sources.items():
        for m in re.finditer(r"^data class (\w+)\s*\(", text, re.M):
            name = m.group(1)
            if name not in registered:
                continue
            # السطور اللي فوق التعريف على طول: تعليقات وتوثيق وتعليقات
            # توضيحية. لازم يكون فيهم `@Entity`.
            head = text[:m.start()]
            tail = head.rsplit("\n\n", 1)[-1]
            if "@Entity" not in tail:
                line = text.count("\n", 0, m.start()) + 1
                problems.append(
                    f"{f.relative_to(REPO)}:{line}: «{name}» مسجّل في @Database "
                    f"ومفيش @Entity ملزوقة بيه — غالباً كتلة اندسّت بينهم."
                )
    return problems


# ═══════════════════════════════════════════ ٥) opt-in ناقص

# رموز بتحتاج @OptIn، والأنوتيشن اللي بتفتحها.
#
# الجدول ده **مبني على اللي وقعنا فيه فعلاً** مش على كل API تجريبي في
# Compose: البناء بيعامل الـopt-in الناقص كـخطأ، فنسيان سطر واحد بيكلّف
# خمس دقايق بناء. أي رمز بيتضاف هنا لازم يتجرّب على الشجرة الحالية الأول —
# لو طلّع نتيجة على كود شغّال يبقى الصف غلط، مش الكود.
#
# الفحص على مستوى **الملف** مش الدالة: بيمسك النسيان الكامل (اللي بيحصل)،
# ومابيمسكش @OptIn متحطّة على دالة غير اللي بتستخدم الرمز. ده مقصود —
# صفر إنذار كاذب أهم من تغطية كاملة في فحص بيجري قبل كل بناء.
OPT_IN = {
    "ExperimentalMaterial3Api": (
        "ModalBottomSheet",
        "rememberModalBottomSheetState",
        "BottomSheetScaffold",
        "rememberBottomSheetScaffoldState",
    ),
    "ExperimentalLayoutApi": (
        "FlowRow",
        "FlowColumn",
    ),
    "ExperimentalFoundationApi": (
        "stickyHeader",
    ),
}


def missing_opt_in(sources: dict) -> list:
    out = []
    for f, text in sources.items():
        for annotation, symbols in OPT_IN.items():
            hits = [
                s for s in symbols
                if re.search(r"(?<![\w.])" + s + r"\s*[(<]", text)
            ]
            if not hits:
                continue
            # التعريف نفسه (لو المشروع عرّف رمز بنفس الاسم) مش استدعاء.
            if re.search(r"fun\s+(" + "|".join(hits) + r")\s*[(<]", text):
                continue
            if annotation in re.findall(r"@(?:file:)?OptIn\(([^)]*)\)", text) \
                    or any(annotation in g for g in re.findall(r"@(?:file:)?OptIn\(([^)]*)\)", text)):
                continue
            line = text.count("\n", 0, text.index(hits[0])) + 1
            out.append(
                f"{f.relative_to(REPO)}:{line}: "
                f"{hits[0]} محتاج @OptIn({annotation}::class) — من غيره "
                f"الكومبايلر بيرمي خطأ مش تحذير."
            )
    return out


def main() -> int:
    files = sorted(SRC.rglob("*.kt"))
    sources = {f: strip_noise(f.read_text(encoding="utf-8")) for f in files}

    # ١) اجمع التعريفات.
    last_param: dict[str, str] = {}
    seen: dict[str, int] = {}
    for f, text in sources.items():
        for m in DECL.finditer(text):
            name = m.group(1)
            seen[name] = seen.get(name, 0) + 1
            close = match_paren(text, m.end() - 1)
            if close < 0:
                continue
            params = split_top_level(text[m.end():close])
            last_param[name] = params[-1] if params else ""

    # التحميل الزائد بيخلّي "آخر بارامتر" غامض — بنسيبه.
    known = {n: p for n, p in last_param.items() if seen.get(n) == 1}

    problems = []
    for f, text in sources.items():
        for name, param in known.items():
            if is_function_type(param):
                continue
            for m in re.finditer(r"(?<![\w.])" + name + r"\s*\(", text):
                # التعريف نفسه مش استدعاء.
                head = text.rfind("\n", 0, m.start()) + 1
                if re.match(r"\s*(?:private |internal |public )?fun\s", text[head:m.start() + 1]):
                    continue
                close = match_paren(text, m.end() - 1)
                if close < 0:
                    continue
                rest = text[close + 1:close + 40].lstrip()
                if rest.startswith("{"):
                    line = text.count("\n", 0, m.start()) + 1
                    problems.append(
                        f"{f.relative_to(REPO)}:{line}: "
                        f"لامدا لاحقة على {name}(...) — آخر بارامتر فيها "
                        f"«{param.strip()}» مش دالة. حط الـlambda كوسيط عادي."
                    )

    problems += duplicate_imports(files)
    problems += non_exhaustive_whens(sources, enum_values(sources))
    problems += orphan_entities(sources)
    problems += missing_opt_in(sources)

    if problems:
        print("مشاكل بتوقّع البناء:\n")
        for p in problems:
            print("  " + p)
        print(f"\n{len(problems)} موضع.")
        return 1
    print(f"فحص سريع: تمام — {len(known)} دالة و{len(files)} ملف.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
