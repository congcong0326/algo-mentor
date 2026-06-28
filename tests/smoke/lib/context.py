from __future__ import annotations

import os
import re
import secrets
from dataclasses import dataclass
from datetime import UTC, datetime
from pathlib import Path


DEFAULT_BASE_URL = "http://localhost:8080"
DEFAULT_REPORT_DIR = "reports/smoke"
DEFAULT_SUITE = "core"


@dataclass(frozen=True)
class SmokeContext:
  repo_root: Path
  base_url: str
  report_dir: Path
  run_id: str
  suite: str
  case: str | None
  tags: tuple[str, ...]
  keep_data: bool
  email: str
  password: str
  display_name: str
  goal: str

  @classmethod
  def from_env(cls, repo_root: Path) -> "SmokeContext":
    run_id = os.getenv("SMOKE_RUN_ID") or generate_run_id()
    report_dir = Path(os.getenv("SMOKE_REPORT_DIR", DEFAULT_REPORT_DIR))
    if not report_dir.is_absolute():
      report_dir = repo_root / report_dir
    tags = tuple(
        tag.strip()
        for tag in os.getenv("SMOKE_TAGS", "").split(",")
        if tag.strip()
    )
    return cls(
        repo_root=repo_root,
        base_url=os.getenv("SMOKE_BASE_URL", DEFAULT_BASE_URL).rstrip("/"),
        report_dir=report_dir,
        run_id=run_id,
        suite=os.getenv("SMOKE_SUITE", DEFAULT_SUITE).strip() or DEFAULT_SUITE,
        case=empty_to_none(os.getenv("SMOKE_CASE")),
        tags=tags,
        keep_data=os.getenv("SMOKE_KEEP_DATA", "false").strip().lower() == "true",
        email=f"smoke+{sanitize_run_id(run_id)}@example.test",
        password="SmokePassword-12345",
        display_name=f"Smoke {run_id}",
        goal=f"Smoke {run_id} Java algorithm interview plan",
    )

  def hurl_variables(self) -> dict[str, str]:
    return {
        "base_url": self.base_url,
        "run_id": self.run_id,
        "smoke_email": self.email,
        "smoke_password": self.password,
        "smoke_display_name": self.display_name,
        "smoke_goal": self.goal,
        "smoke_keep_data": str(self.keep_data).lower(),
    }


def generate_run_id() -> str:
  timestamp = datetime.now(UTC).strftime("%Y%m%d%H%M%S")
  suffix = secrets.token_hex(4)
  return f"{timestamp}-{suffix}"


def sanitize_run_id(run_id: str) -> str:
  sanitized = re.sub(r"[^a-zA-Z0-9._+-]", "-", run_id).strip(".-")
  return sanitized or generate_run_id()


def empty_to_none(value: str | None) -> str | None:
  if value is None:
    return None
  value = value.strip()
  return value or None
