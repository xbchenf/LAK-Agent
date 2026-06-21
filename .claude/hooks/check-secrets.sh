#!/usr/bin/env bash
# =============================================================================
# Hook: PreToolUse (matcher: "Write|Edit")
# 用途: 在写文件前检查是否包含敏感信息
# LAK-Agent 定制: 检测百炼 API Key / JWT Secret / 数据库密码硬编码
# =============================================================================

LOG_DIR="${CLAUDE_PROJECT_DIR}/.claude/hook-logs"
mkdir -p "$LOG_DIR"

TIMESTAMP=$(date '+%Y-%m-%d %H:%M:%S')
PAYLOAD=$(cat)

# ----- 解析 payload 提取文件路径和内容 -----
PYTHON=""
if command -v python &>/dev/null; then
  PYTHON="python"
elif command -v python3 &>/dev/null; then
  PYTHON="python3"
fi

if [ -z "$PYTHON" ]; then
  exit 0
fi

FILE_PATH=$("$PYTHON" -c "
import sys, json
data = json.load(sys.stdin)
ti = data.get('tool_input', {})
print(ti.get('file_path', ''))
" <<< "$PAYLOAD" 2>/dev/null)

# 排除二进制文件和非文本扩展名，其余全部扫描
EXT="${FILE_PATH##*.}"
case "$EXT" in
  jar|war|class|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf|eot|pdf|zip|tar|gz|xz|7z|exe|dll|so|dylib) exit 0 ;;
esac

FILE_CONTENT=$("$PYTHON" -c "
import sys, json
data = json.load(sys.stdin)
ti = data.get('tool_input', {})
content = ti.get('new_string', ti.get('content', ''))
print(content[:5000])
" <<< "$PAYLOAD" 2>/dev/null)

# =============================================================================
# 敏感信息检测 — 全部用 Python 做（跨平台，避免 grep -P 兼容性问题）
# =============================================================================
BLOCKED=false

# 百炼 API Key — 匹配 = / : 赋值，含引号
if echo "$FILE_CONTENT" | "$PYTHON" -c "
import sys
import re
content = sys.stdin.read()
if re.search(r'DASHSCOPE_API_KEY\s*[:=]\s*[\"\\x27]?[a-zA-Z0-9_\-\.]{20,}', content):
    sys.exit(1)
" 2>/dev/null; then
  :
else
  echo "[$TIMESTAMP] SECRET-BLOCKED: DASHSCOPE_API_KEY hardcoded in $FILE_PATH" >> "$LOG_DIR/blocked-secrets.log"
  echo "==============================================" >&2
  echo " [安全拦截] 检测到 百炼 API Key 硬编码!" >&2
  echo " 文件: $FILE_PATH" >&2
  echo " DASHSCOPE_API_KEY 必须通过环境变量 \${DASHSCOPE_API_KEY} 注入" >&2
  echo "==============================================" >&2
  exit 1
fi

# JWT Secret
if echo "$FILE_CONTENT" | "$PYTHON" -c "
import sys, re
content = sys.stdin.read()
if re.search(r'JWT_SECRET\s*[:=]\s*[\"\\x27]?[a-zA-Z0-9_\-\.]{20,}', content):
    sys.exit(1)
" 2>/dev/null; then
  :
else
  echo "[$TIMESTAMP] SECRET-BLOCKED: JWT_SECRET hardcoded in $FILE_PATH" >> "$LOG_DIR/blocked-secrets.log"
  echo "==============================================" >&2
  echo " [安全拦截] 检测到 JWT Secret 硬编码!" >&2
  echo " 文件: $FILE_PATH" >&2
  echo " JWT_SECRET 必须通过环境变量注入" >&2
  echo "==============================================" >&2
  exit 1
fi

# 数据库密码 — 硬编码明文密码
echo "$FILE_CONTENT" | "$PYTHON" -c "
import sys, re
content = sys.stdin.read()
# 匹配 password/secret 赋值，排除环境变量引用 \${
matches = re.findall(r'(?:password|passwd|MYSQL_PASSWORD|secret)\s*[:=]\s*[\"\\x27](?!.*\$\{)([^\"\\x27]{4,})[\"\\x27]', content, re.IGNORECASE)
if matches:
    for m in matches:
        if not m.startswith('\${') and m not in ('localhost', 'dev-password', 'changeme', 'your-password'):
            sys.exit(1)
" 2>/dev/null && exit 0

echo "[$TIMESTAMP] SECRET-BLOCKED: password hardcoded in $FILE_PATH" >> "$LOG_DIR/blocked-secrets.log"
echo "==============================================" >&2
echo " [安全拦截] 检测到数据库密码硬编码!" >&2
echo " 文件: $FILE_PATH" >&2
echo " 密码必须通过环境变量注入（\${MYSQL_PASSWORD}）" >&2
echo "==============================================" >&2
exit 1
