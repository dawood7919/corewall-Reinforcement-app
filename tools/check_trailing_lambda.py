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

٢. استيراد متكرر لنفس الاسم في نفس الملف.

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
