#!/usr/bin/env python3
"""
يتأكد إن الفهارس اللي الترحيلات بتعملها = الفهارس اللي Room متوقّعها.

ليه السكربت ده موجود:
Room بيقارن المخطط المتولّد من الـ`@Entity` بالقاعدة الحقيقية بعد كل ترحيل،
وبيرمي `IllegalStateException` لو فيه أي اختلاف — حتى لو الاختلاف فهرس واحد
زيادة. النتيجة على الجهاز إن التطبيق بيقفل بعد ثانيتين من الفتح، من غير أي
رسالة تفهّم المستخدم حصل إيه. والغلطة دي مستحيل الكومبايلر يمسكها لأن
الترحيلات مجرد نصّ SQL جوّه `execSQL`.

فالفحص هنا بيقرا الاتنين ويقارنهم كمجموعتين:
  • الفهارس المعلَنة في المخطط المصدَّر (`app/schemas/<db>/<version>.json`)
  • الفهارس اللي أي ترحيل بيعملها بـ`CREATE INDEX`

لازم يبقوا متطابقين بالظبط:
  فهرس في الكيان ومش في الترحيل  → المستخدمين القدام هيقع عندهم التطبيق.
  فهرس في الترحيل ومش في الكيان  → نفس الحكاية، بالعكس.

بيتشغّل في الـCI بعد البناء وقبل نشر الـAPK.
"""

from __future__ import annotations

import json
import pathlib
import re
import sys

REPO = pathlib.Path(__file__).resolve().parent.parent
SCHEMA_DIR = REPO / "app" / "schemas"
MIGRATIONS = REPO / "app/src/main/java/com/corewall/qaqc/data/db/AppDatabase.kt"

# CREATE [UNIQUE] INDEX [IF NOT EXISTS] `name` ON `table` (`col`, `col`)
CREATE_INDEX = re.compile(
    r"CREATE\s+(?P<unique>UNIQUE\s+)?INDEX\s+(?:IF\s+NOT\s+EXISTS\s+)?"
    r"[`\"]?(?P<name>\w+)[`\"]?\s+ON\s+[`\"]?(?P<table>\w+)[`\"]?\s*"
    r"\((?P<cols>[^)]*)\)",
    re.IGNORECASE,
)


def normalise(unique: bool, name: str, table: str, cols) -> tuple:
    """شكل واحد للفهرس عشان المقارنة تبقى على المعنى مش على الكتابة."""
    if isinstance(cols, str):
        cols = [c.strip().strip("`\"") for c in cols.split(",")]
    return (table, name, tuple(cols), bool(unique))


def latest_schema() -> pathlib.Path:
    """أعلى نسخة مصدَّرة — دي اللي التطبيق شغّال عليها دلوقتي."""
    files = list(SCHEMA_DIR.glob("*/*.json"))
    if not files:
        raise SystemExit(
            f"✗ مفيش مخطط مصدَّر في {SCHEMA_DIR}.\n"
            "  المفروض `exportSchema = true` على @Database و"
            "`ksp { arg(\"room.schemaLocation\", ...) }` في build.gradle.kts،\n"
            "  والفحص ده يجري **بعد** البناء."
        )
    return max(files, key=lambda p: int(p.stem))


def from_schema(path: pathlib.Path) -> set[tuple]:
    data = json.loads(path.read_text(encoding="utf-8"))
    found = set()
    for entity in data["database"]["entities"]:
        table = entity["tableName"]
        for idx in entity.get("indices", []):
            found.add(
                normalise(idx.get("unique", False), idx["name"], table, idx["columnNames"])
            )
    return found


# نصّين ملزوقين بـ`+` في Kotlin: `"…" +\n    "…"`. بيتشالوا قبل البحث.
KOTLIN_CONCAT = re.compile(r'"\s*\+\s*"')


def join_literals(src: str) -> str:
    """
    بيلزق النصوص المقسومة على أكتر من سطر في Kotlin.

    من غير ده، `CREATE INDEX … ` + `ON …` مكتوبة على سطرين مابتتشافش —
    والفحص بيقول إن الترحيل مش بيعمل الفهرس وهو بيعمله. الاتجاه العكسي
    أخطر: فهرس **بيتعمل** في ترحيل ومش معلَن على الكيان بيعدّي من غير ما
    حد ياخد باله، والتطبيق بيقفل عند المستخدم.
    """
    return KOTLIN_CONCAT.sub("", src)


def from_migrations(path: pathlib.Path) -> set[tuple]:
    src = join_literals(path.read_text(encoding="utf-8"))
    return {
        normalise(bool(m.group("unique")), m.group("name"), m.group("table"), m.group("cols"))
        for m in CREATE_INDEX.finditer(src)
    }


# ── جداول: مش الفهارس بس
#
# الفهرس الناقص بيوقّع التطبيق، وكذلك **الجدول** اللي جملة إنشائه في
# الترحيل مش مطابقة للي Room متوقّعه. ودي أصعب في الملاحظة: جدول FTS
# مثلاً بيتعرّف بجملة `CREATE VIRTUAL TABLE … USING FTS4(…)` وأي فرق في
# الأعمدة أو المقسّم بيرمي استثناء بعد الترحيل مباشرة.
EXEC_SQL = re.compile(r'execSQL\(\s*"((?:[^"\\\\]|\\\\.)*)"', re.S)


def canon_sql(sql: str) -> str:
    """توحيد الشكل قبل المقارنة — المسافات وعلامات التنصيص مش فارقة."""
    t = sql.replace("`", "").replace('"', "").replace("'", "")
    t = re.sub(r"\s+", " ", t)
    t = t.replace("IF NOT EXISTS ", "")
    t = t.replace("( ", "(").replace(" )", ")")
    return t.strip().rstrip(";").lower()


def tables_from_schema(path: pathlib.Path) -> dict:
    data = json.loads(path.read_text(encoding="utf-8"))
    out = {}
    for entity in data["database"]["entities"]:
        sql = entity.get("createSql", "")
        if sql:
            out[entity["tableName"]] = canon_sql(
                sql.replace("${TABLE_NAME}", entity["tableName"])
            )
    return out


def newest_migration_body(src: str, version: int) -> str:
    """
    جسم الترحيل للنسخة الحالية بس.

    ## ليه النسخة الأخيرة بس
    الجدول اللي اتعمل في نسخة قديمة بيتعدّل بـ`ALTER TABLE` في الترحيلات
    اللي بعدها، فجملة إنشائه الأصلية **بتختلف عن المخطط الحالي بشكل صحيح**.
    مقارنتها بيه بتطلّع إنذار كاذب على كود سليم — وفحص بيكدب بيتلغى.

    الترحيل للنسخة الحالية هو الوحيد اللي جداوله المفروض تطابق المخطط
    بالحرف: مفيش حاجة عدّلت عليها بعده.
    """
    head = re.search(rf"private val MIGRATION_\d+_{version}\b", src)
    if not head:
        return ""
    rest = src[head.end():]
    nxt = re.search(r"private val (MIGRATION_\d+_\d+|\w+)\s*=", rest)
    return rest[: nxt.start()] if nxt else rest


def tables_from_migrations(path: pathlib.Path, version: int) -> dict:
    """
    الجداول اللي الترحيل الأخير بيعملها.

    القراية من نصّ `execSQL("…")` نفسه، مش بتعبير عام بيدوّر على `CREATE`:
    التعبير العام مابيعرفش الجملة بتخلص فين، فبياخد معاها القوس بتاع
    `execSQL` واللي بعده — وده كان بيطلّع فروق مش موجودة.
    """
    src = join_literals(path.read_text(encoding="utf-8"))
    body = newest_migration_body(src, version)
    out = {}
    for m in EXEC_SQL.finditer(body):
        stmt = m.group(1)
        found = re.match(
            r"\s*CREATE\s+(?:VIRTUAL\s+)?TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?[`\"]?(\w+)",
            stmt, re.I,
        )
        if found:
            out[found.group(1)] = canon_sql(stmt)
    return out


def check_tables(schema: pathlib.Path, migrations: pathlib.Path) -> list:
    """بيقارن جداول الترحيل الأخير باللي Room متوقّعه."""
    version = int(schema.stem)
    want = tables_from_schema(schema)
    got = tables_from_migrations(migrations, version)
    problems = []
    for table, sql in got.items():
        expected = want.get(table)
        if expected is None:
            continue
        if sql != expected:
            problems.append(
                f"جدول «{table}» مختلف بين الترحيل والمخطط:\n"
                f"      الترحيل : {sql}\n"
                f"      Room    : {expected}"
            )
    return problems


def describe(idx: tuple) -> str:
    table, name, cols, unique = idx
    return f"    {table}.{name} ({', '.join(cols)}){' UNIQUE' if unique else ''}"


def main() -> int:
    schema_file = latest_schema()
    declared = from_schema(schema_file)
    created = from_migrations(MIGRATIONS)

    table_problems = check_tables(schema_file, MIGRATIONS)

    if declared == created and not table_problems:
        print(f"✓ فهارس Room متطابقة ({len(declared)}) — {schema_file.relative_to(REPO)}")
        for idx in sorted(declared):
            print(describe(idx))
        return 0

    if table_problems:
        print(f"✗ جداول مش متطابقة — {schema_file.relative_to(REPO)}\n")
        for p in table_problems:
            print("  • " + p)
        print(
            "\n  الحل: خلّي جملة `CREATE TABLE` في الترحيل نفس اللي Room بيولّده،\n"
            "  بنفس الأعمدة وترتيبها وخصائصها. لجداول FTS، المقسّم جزء من الجملة.\n"
        )
        if declared == created:
            return 1

    print(f"✗ المخطط والترحيلات مش متطابقين — {schema_file.relative_to(REPO)}\n")
    missing = declared - created
    extra = created - declared
    if missing:
        print("  معلَن على الكيان ومفيش ترحيل بيعمله")
        print("  (المستخدم اللي بيحدّث هيقع عنده التطبيق أول ما يفتح):")
        for idx in sorted(missing):
            print(describe(idx))
        print()
    if extra:
        print("  الترحيل بيعمله ومش معلَن على الكيان")
        print("  (نفس النتيجة — Room بيرفض أي فهرس زيادة):")
        for idx in sorted(extra):
            print(describe(idx))
        print()
    print(
        "  الحل: عرّف الفهرس بـ`@Entity(indices = [Index(...)])` وسيب Room يسمّيه،\n"
        "  وخلّي جملة `CREATE INDEX` في الترحيل تستخدم نفس الاسم بالحرف\n"
        "  (`index_<جدول>_<عمود>_<عمود>`)."
    )
    return 1


if __name__ == "__main__":
    sys.exit(main())
