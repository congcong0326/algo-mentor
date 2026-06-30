#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
runtime_dir="${NGINX_RUNTIME_DIR:-$repo_root/.local/nginx}"
conf_file="$runtime_dir/nginx.conf"
pid_file="$runtime_dir/nginx.pid"
log_dir="$runtime_dir/logs"

proxy_listen_host="${PROXY_LISTEN_HOST:-0.0.0.0}"
proxy_port="${PROXY_PORT:-8080}"
api_host="${API_HOST:-127.0.0.1}"
api_port="${API_PORT:-18080}"
prometheus_host="${PROMETHEUS_HOST:-127.0.0.1}"
prometheus_port="${PROMETHEUS_PORT:-9090}"

action="${1:-start}"

nginx_bin() {
  if ! command -v nginx >/dev/null 2>&1; then
    echo "nginx is not installed. Install it first, for example: sudo apt-get update && sudo apt-get install -y nginx" >&2
    exit 1
  fi
  command -v nginx
}

render_config() {
  mkdir -p "$log_dir" \
    "$runtime_dir/client_body_temp" \
    "$runtime_dir/proxy_temp" \
    "$runtime_dir/fastcgi_temp" \
    "$runtime_dir/uwsgi_temp" \
    "$runtime_dir/scgi_temp"

  cat > "$conf_file" <<EOF
worker_processes  1;
daemon on;
pid nginx.pid;
error_log logs/error.log warn;

events {
    worker_connections  1024;
}

http {
    include       /etc/nginx/mime.types;
    default_type  application/octet-stream;

    access_log logs/access.log;
    sendfile on;
    keepalive_timeout 65;

    client_body_temp_path client_body_temp;
    proxy_temp_path proxy_temp;
    fastcgi_temp_path fastcgi_temp;
    uwsgi_temp_path uwsgi_temp;
    scgi_temp_path scgi_temp;

    upstream algo_mentor_api {
        server $api_host:$api_port;
        keepalive 16;
    }

    upstream algo_mentor_prometheus {
        server $prometheus_host:$prometheus_port;
        keepalive 4;
    }

    server {
        listen $proxy_listen_host:$proxy_port;
        server_name localhost;
        client_max_body_size 20m;

        location = /nginx-health {
            access_log off;
            default_type text/plain;
            return 200 "ok\\n";
        }

        location = /prometheus {
            return 308 /prometheus/;
        }

        location /prometheus/ {
            proxy_pass http://algo_mentor_prometheus/;
            proxy_http_version 1.1;
            proxy_set_header Host \$host;
            proxy_set_header X-Real-IP \$remote_addr;
            proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Host \$host;
            proxy_set_header X-Forwarded-Port \$server_port;
            proxy_set_header X-Forwarded-Prefix /prometheus;
            proxy_set_header X-Forwarded-Proto \$scheme;
            proxy_redirect / /prometheus/;
        }

        location / {
            proxy_pass http://algo_mentor_api;
            proxy_http_version 1.1;
            proxy_set_header Host \$host;
            proxy_set_header X-Real-IP \$remote_addr;
            proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Host \$host;
            proxy_set_header X-Forwarded-Port \$server_port;
            proxy_set_header X-Forwarded-Proto \$scheme;
            proxy_set_header Connection "";
            proxy_buffering off;
            proxy_cache off;
            proxy_read_timeout 370s;
            proxy_send_timeout 370s;
        }
    }
}
EOF
}

is_running() {
  if [ ! -s "$pid_file" ]; then
    return 1
  fi
  kill -0 "$(cat "$pid_file")" 2>/dev/null
}

start_proxy() {
  local nginx
  nginx="$(nginx_bin)"
  render_config
  "$nginx" -p "$runtime_dir/" -c "$conf_file" -t
  if is_running; then
    "$nginx" -p "$runtime_dir/" -c "$conf_file" -s reload
    echo "nginx proxy reloaded on http://localhost:$proxy_port"
  else
    "$nginx" -p "$runtime_dir/" -c "$conf_file"
    echo "nginx proxy started on http://localhost:$proxy_port"
  fi
}

stop_proxy() {
  local nginx
  nginx="$(nginx_bin)"
  if is_running; then
    "$nginx" -p "$runtime_dir/" -c "$conf_file" -s stop
    echo "nginx proxy stopped"
  else
    echo "nginx proxy is not running"
  fi
}

status_proxy() {
  if is_running; then
    echo "nginx proxy is running, pid $(cat "$pid_file")"
    echo "public entry: http://localhost:$proxy_port"
    echo "java upstream: http://$api_host:$api_port"
    echo "prometheus upstream: http://$prometheus_host:$prometheus_port via /prometheus/"
  else
    echo "nginx proxy is not running"
  fi
}

case "$action" in
  start)
    start_proxy
    ;;
  stop)
    stop_proxy
    ;;
  restart)
    stop_proxy
    start_proxy
    ;;
  status)
    status_proxy
    ;;
  config)
    render_config
    echo "$conf_file"
    ;;
  test)
    render_config
    "$(nginx_bin)" -p "$runtime_dir/" -c "$conf_file" -t
    ;;
  *)
    echo "Usage: $0 {start|stop|restart|status|config|test}" >&2
    exit 2
    ;;
esac
