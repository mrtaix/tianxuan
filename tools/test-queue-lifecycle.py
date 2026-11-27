#!/usr/bin/env python3
"""Regression SQL của vòng đời queue; không thay thế test Room/Kotlin hoặc UI."""
from pathlib import Path
import re
import sqlite3
import unittest

ROOT = Path(__file__).resolve().parents[1]
DAO = ROOT / "core/database/src/main/java/top/wkbin/tianxuan/core/database/HarnessRuntimeDao.kt"


def query_for(method):
    source = DAO.read_text(encoding="utf-8")
    match = re.search(r'@Query\("([^"\n]+)"\)\s+suspend fun ' + re.escape(method) + r'\(', source)
    if match is None:
        raise AssertionError(f"Không tìm thấy SQL của {method}")
    return match.group(1)


class QueueLifecycleSqlTest(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(":memory:")
        self.addCleanup(self.db.close)
        self.db.execute("""CREATE TABLE harness_queue_items (
            id TEXT PRIMARY KEY, sessionId TEXT NOT NULL, laneName TEXT NOT NULL,
            operationId TEXT, queueType TEXT NOT NULL, createdAt INTEGER NOT NULL,
            payloadJson TEXT NOT NULL
        )""")
        rows = [
            ("b", "s", "main", "a", "next_run", 1, '{"text":"B"}'),
            ("c", "s", "main", "a", "next_run", 2, '{"text":"C"}'),
            ("d", "s", "main", None, "next_run", 3, '{"text":"D"}'),
            ("steer", "s", "main", "a", "steer", 4, "{}"),
            ("follow", "s", "main", "a", "follow_up", 5, "{}"),
            ("other", "s", "worker", "other-op", "steer", 6, "{}"),
            ("other-next", "s", "worker", "other-op", "next_run", 7, "{}"),
        ]
        self.db.executemany("INSERT INTO harness_queue_items VALUES (?, ?, ?, ?, ?, ?, ?)", rows)
        self.db.commit()

    def next_items(self):
        return self.db.execute(
            "SELECT id, operationId, payloadJson FROM harness_queue_items "
            "WHERE sessionId = 's' AND laneName = 'main' AND queueType = 'next_run' ORDER BY createdAt, id"
        ).fetchall()

    def finish_queue(self):
        # Dùng chính SQL khai báo trong DAO thay vì chép một bản thuật toán vào test.
        with self.db:
            self.db.execute(query_for("detachNextRunQueue"), {"operationId": "a"})
            self.db.execute(query_for("deleteOperationQueue"), {"operationId": "a"})

    def test_old_cleanup_reproduces_lost_messages(self):
        self.db.execute(query_for("deleteOperationQueue"), {"operationId": "a"})
        self.assertEqual(["d"], [row[0] for row in self.next_items()])

    def test_finish_preserves_legacy_and_new_next_run_in_order(self):
        self.finish_queue()
        self.assertEqual(
            [("b", None, '{"text":"B"}'), ("c", None, '{"text":"C"}'), ("d", None, '{"text":"D"}')],
            self.next_items(),
        )
        self.assertEqual(
            [("other", "other-op"), ("other-next", "other-op")],
            self.db.execute("SELECT id, operationId FROM harness_queue_items WHERE laneName = 'worker' ORDER BY createdAt").fetchall(),
        )
        self.assertEqual(0, self.db.execute(
            "SELECT COUNT(*) FROM harness_queue_items WHERE id IN ('steer', 'follow')"
        ).fetchone()[0])
        # Reclaim/kết thúc lặp lại không làm mất hoặc nhân đôi tin đang chờ.
        self.finish_queue()
        self.assertEqual(["b", "c", "d"], [row[0] for row in self.next_items()])
        self.db.execute(query_for("deleteQueueItem"), {"itemId": "b"})
        self.assertEqual(["c", "d"], [row[0] for row in self.next_items()])

    def test_finish_transaction_calls_detach_before_delete(self):
        source = DAO.read_text(encoding="utf-8")
        body = source.split("suspend fun finishOperation(", 1)[1].split("\n    }", 1)[0]
        self.assertLess(body.index("detachNextRunQueue("), body.index("deleteOperationQueue("))
        self.assertIn("@Transaction\n    suspend fun finishOperation", source)


if __name__ == "__main__":
    unittest.main(verbosity=2)
