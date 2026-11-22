#!/usr/bin/env python3
"""scripts/check-r8-reflective-constructors.py on small DEX files built with javac + d8 (#4175).

Each case compiles a few classes standing in for an R8 output (obfuscated names, a merged base class), writes the
matching mapping lines by hand, and checks what the script reports. Needs a JDK and the Android SDK build-tools d8.
Run: python3 -m unittest discover -s scripts/tests -p test_check_r8_reflective_constructors.py
"""
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts/check-r8-reflective-constructors.py"


def find_d8():
    sdk = os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME") or "/opt/android-sdk"
    tools = sorted(Path(sdk, "build-tools").glob("*/d8"))
    return str(tools[-1]) if tools else shutil.which("d8")


D8 = find_d8()


@unittest.skipUnless(D8 and shutil.which("javac"), "needs javac and the Android SDK build-tools d8")
class ReflectiveConstructorCheckTest(unittest.TestCase):
    def build(self, sources, mapping, minimum=False):
        """Compile {"o/A.java": src} to DEX in a temp dir and run the check; return (exit code, stdout, stderr)."""
        tmp = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, tmp)
        src, cls, dex = tmp / "src", tmp / "classes", tmp / "dex"
        for rel, text in sources.items():
            (src / rel).parent.mkdir(parents=True, exist_ok=True)
            (src / rel).write_text(text)
        cls.mkdir()
        dex.mkdir()
        subprocess.run(["javac", "--release", "11", "-d", str(cls)] + [str(p) for p in src.rglob("*.java")],
                       check=True)
        subprocess.run([D8, "--min-api", "23", "--output", str(dex)] + [str(p) for p in cls.rglob("*.class")],
                       check=True, capture_output=True)
        (tmp / "mapping.txt").write_text("# pg_map_id: test\n" + mapping)
        r = subprocess.run([sys.executable, "-I", str(SCRIPT), str(tmp / "mapping.txt"), "--dex-dir", str(dex)]
                           + ([] if minimum else ["--no-minimum"]),
                           capture_output=True, text=True)
        return r.returncode, r.stdout, r.stderr

    # The 6.0.1279 shape: R8 merged androidx.room.RoomDatabase into WorkDatabase (its only subclass), and with Room
    # 2.2.5's member-less keep rule it made WorkDatabase_Impl abstract and dropped its constructor.
    WORK_DB_MAPPING = (
        "androidx.work.impl.WorkDatabase -> androidx.work.impl.WorkDatabase:\n"
        "    androidx.sqlite.db.SupportSQLiteOpenHelper androidx.room.RoomDatabase.mOpenHelper -> a\n"
        "    1:5:void androidx.room.RoomDatabase.assertNotMainThread():263:263 -> a\n"
        "androidx.work.impl.WorkDatabase_Impl -> androidx.work.impl.WorkDatabase_Impl:\n"
    )

    def test_merged_room2_base_with_abstract_impl_fails(self):
        code, out, err = self.build({
            "androidx/work/impl/WorkDatabase.java":
                "package androidx.work.impl; public abstract class WorkDatabase {}",
            "androidx/work/impl/WorkDatabase_Impl.java":
                "package androidx.work.impl; public abstract class WorkDatabase_Impl extends WorkDatabase "
                "{ private WorkDatabase_Impl(int unused) {} }",
        }, self.WORK_DB_MAPPING)
        self.assertEqual(1, code, out + err)
        self.assertIn("androidx.room.RoomDatabase: 1 leaf classes checked, 1 broken", out)
        self.assertIn("androidx.work.impl.WorkDatabase_Impl: abstract, no no-argument constructor", err)

    def test_merged_room2_base_with_concrete_impl_passes(self):
        code, out, err = self.build({
            "androidx/work/impl/WorkDatabase.java":
                "package androidx.work.impl; public abstract class WorkDatabase {}",
            "androidx/work/impl/WorkDatabase_Impl.java":
                "package androidx.work.impl; public final class WorkDatabase_Impl extends WorkDatabase {}",
        }, self.WORK_DB_MAPPING)
        self.assertEqual(0, code, out + err)
        self.assertIn("androidx.room.RoomDatabase: 1 leaf classes checked\n", out)

    def test_room2_database_left_without_its_impl_fails(self):
        # R8 removed the _Impl entirely: the abstract database class itself is the leaf.
        code, out, err = self.build({
            "androidx/work/impl/WorkDatabase.java":
                "package androidx.work.impl; public abstract class WorkDatabase {}",
        }, self.WORK_DB_MAPPING)
        self.assertEqual(1, code, out + err)
        self.assertIn("androidx.work.impl.WorkDatabase: abstract", err)

    def test_renamed_base_and_missing_no_arg_constructor_fail(self):
        # Room 3 base kept as a class but renamed; the database impl only has a constructor with arguments.
        code, out, err = self.build({
            "o/a.java": "package o; public abstract class a {}",
            "o/b.java": "package o; public abstract class b extends a {}",
            "o/c.java": "package o; public final class c extends b { public c(String s) {} }",
        }, "androidx.room3.RoomDatabase -> o.a:\n"
           "net.bible.android.database.BookmarkDatabase -> o.b:\n"
           "net.bible.android.database.BookmarkDatabase_Impl -> o.c:\n")
        self.assertEqual(1, code, out + err)
        self.assertIn("net.bible.android.database.BookmarkDatabase_Impl (o.c): no no-argument constructor", err)

    def test_member_moved_from_a_present_base_does_not_make_a_class_a_subclass(self):
        # R8 moved a static field of the (still present) Room 3 base into an unrelated lambda-like class.
        code, out, err = self.build({
            "o/a.java": "package o; public abstract class a {}",
            "o/c.java": "package o; public final class c extends a {}",
            "o/z.java": "package o; public final class z { public z(int captured) {} }",
        }, "androidx.room3.RoomDatabase -> o.a:\n"
           "net.bible.android.database.BookmarkDatabase_Impl -> o.c:\n"
           "net.bible.Lambda -> o.z:\n"
           "    java.lang.Object androidx.room3.RoomDatabase.LOCK -> b\n"
           "    1:1:void androidx.room3.RoomDatabase.helper():5:5 -> a\n")
        self.assertEqual(0, code, out + err)
        self.assertIn("androidx.room3.RoomDatabase: 1 leaf classes checked\n", out)

    def test_interface_base_implementations_are_checked(self):
        code, out, err = self.build({
            "o/i.java": "package o; public interface i {}",
            "o/ok.java": "package o; public class ok implements i {}",
            "o/bad.java": "package o; public abstract class bad implements i {}",
        }, "androidx.startup.Initializer -> o.i:\n"
           "androidx.work.WorkManagerInitializer -> o.ok:\n"
           "androidx.glance.Broken -> o.bad:\n")
        self.assertEqual(1, code, out + err)
        self.assertIn("androidx.startup.Initializer: 2 leaf classes checked, 1 broken", out)
        self.assertIn("androidx.glance.Broken (o.bad): abstract", err)

    def test_inlined_base_frame_does_not_make_a_class_a_subclass(self):
        # Only an inner inlined frame names RoomDatabase; the outer frame (the real method) belongs to the class.
        code, out, err = self.build({
            "o/f.java": "package o; public abstract class f {}",
        }, "net.bible.Foo -> o.f:\n"
           "    1:2:void androidx.room.RoomDatabase.beginTransaction():351:351 -> a\n"
           "    1:2:void net.bible.Foo.run():10 -> a\n")
        self.assertEqual(0, code, out + err)
        self.assertIn("androidx.room.RoomDatabase: 0 leaf classes checked", out)

    def test_default_bases_missing_a_known_base_fail_the_minimum(self):
        # Only a Room 3 database: Room 2, InputMerger and ActionCallback found no leaf, which must not pass as "0 checked".
        code, out, err = self.build({
            "o/a.java": "package o; public abstract class a {}",
            "o/c.java": "package o; public final class c extends a {}",
        }, "androidx.room3.RoomDatabase -> o.a:\nnet.bible.DbImpl -> o.c:\n", minimum=True)
        self.assertEqual(1, code, out + err)
        self.assertIn("androidx.room.RoomDatabase: found 0 leaf classes, expected at least 1", err)
        self.assertIn("androidx.work.InputMerger: found 0 leaf classes", err)
        self.assertNotIn("androidx.room3.RoomDatabase: found", err)


if __name__ == "__main__":
    unittest.main()
