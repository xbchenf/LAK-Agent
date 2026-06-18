#!/usr/bin/env bash
# =============================================================================
# Hook: PreToolUse (matcher: "Write|Edit")
# 用途: 在写文件前检查是否包含敏感信息
# LAK-Agent 定制: 检测百炼 API Key / JWT Secret / 数据库密码 / BCrypt 硬编码
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
# Edit 用 file_path, Write 也用 file_path
print(ti.get('file_path', ''))
" <<< "$PAYLOAD" 2>/dev/null)

# 只检查 Java / YAML / properties 文件
if ! echo "$FILE_PATH" | grep -Eq '\.(java|yml|yaml|properties|xml|json)$'; then
  exit 0
fi

FILE_CONTENT=$("$PYTHON" -c "
import sys, json
data = json.load(sys.stdin)
ti = data.get('tool_input', {})
# Edit 用 new_string, Write 用 content
content = ti.get('new_string', ti.get('content', ''))
# 截取前 5000 字符检查
print(content[:5000])
" <<< "$PAYLOAD" 2>/dev/null)

# =============================================================================
# 敏感信息检测规则
# =============================================================================

# 百炼 API Key 模式
if echo "$FILE_CONTENT" | grep -Eq 'DASHSCOPE_API_KEY["\s:]*["\s:][a-zA-Z0-9_-]{20,}'; then
  echo "[$TIMESTAMP] SECRET-BLOCKED: DASHSCOPE_API_KEY hardcoded in $FILE_PATH" >> "$LOG_DIR/blocked-secrets.log"
  echo "==============================================" >&2
  echo " [安全拦截] 检测到 百炼 API Key 硬编码!" >&2
  echo " 文件: $FILE_PATH" >&2
  echo " DASHSCOPE_API_KEY 必须通过环境变量 \${DASHSCOPE_API_KEY} 注入" >&2
  echo "==============================================" >&2
  exit 1
fi

# JWT Secret 模式
if echo "$FILE_CONTENT" | grep -Eq 'JWT_SECRET["\s:]*["\s:][a-zA-Z0-9_-]{20,}'; then
  echo "[$TIMESTAMP] SECRET-BLOCKED: JWT_SECRET hardcoded in $FILE_PATH" >> "$LOG_DIR/blocked-secrets.log"
  echo "==============================================" >&2
  echo " [安全拦截] 检测到 JWT Secret 硬编码!" >&2
  echo " 文件: $FILE_PATH" >&2
  echo " JWT_SECRET 必须通过环境变量注入" >&2
  echo "==============================================" >&2
  exit 1
fi

# 数据库密码模式（排除 localhost/dev 注释）
if echo "$FILE_CONTENT" | grep -qP '(?<!localhost.*)(?<!dev.*)(password|MYSQL_PASSWORD)\s*[:=]\s*["\x27][^$][^"'\x27]{3,}["\x27]' 2>/dev/null; then
  echo "[$TIMESTAMP] SECRET-WARN: possible password hardcoded in $FILE_PATH" >> "$LOG_DIR/blocked-secrets.log"
fi

exit 0
