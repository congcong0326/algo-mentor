#!/usr/bin/env bash
set -euo pipefail

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker CLI is not installed or not in PATH." >&2
  echo "Install Docker, then retry: make observability-up" >&2
  exit 1
fi

if ! docker --version >/dev/null 2>&1; then
  echo "Docker CLI is installed but docker --version failed." >&2
  exit 1
fi

if ! docker compose version >/dev/null 2>&1; then
  echo "Docker Compose v2 is not available. Expected: docker compose version" >&2
  exit 1
fi

if ! docker info >/dev/null 2>&1; then
  echo "Docker daemon is not reachable." >&2
  echo "Start Docker Desktop or the Docker service, then retry: make observability-up" >&2
  exit 1
fi
