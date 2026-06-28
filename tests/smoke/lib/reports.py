from __future__ import annotations

from pathlib import Path


def prepare_report_dir(report_root: Path, run_id: str) -> Path:
  report_dir = report_root / run_id
  (report_dir / "html").mkdir(parents=True, exist_ok=True)
  (report_dir / "json").mkdir(parents=True, exist_ok=True)
  return report_dir
