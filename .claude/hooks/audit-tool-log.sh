#!/usr/bin/env bash
# =============================================================================
# Hook: PostToolUse + Stop
# 用途: 记录所有工具调用结果 + 会话结束汇总
# LAK-Agent 适配: 追踪文件变更 + 工具使用统计 + 会话摘要
# =============================================================================

LOG_DIR="${CLAUDE_PROJECT_DIR}/.claude/hook-logs"
mkdir -p "$LOG_DIR"

TIMESTAMP=$(date '+%Y-%m-%d %H:%M:%S')
PAYLOAD=$(cat)
HOOK_EVENT="${CLAUDE_HOOK_EVENT:-unknown}"

# ----- 确定 Python 解释器 -----
PYTHON=""
if command -v python &>/dev/null; then
  PYTHON="python"
elif command -v python3 &>/dev/null; then
  PYTHON="python3"
fi

# =============================================================================
# Stop 事件: 汇总本次会话统计
# =============================================================================
if [ "$HOOK_EVENT" = "Stop" ]; then
  SUMMARY_FILE="$LOG_DIR/tool-summary.txt"
  {
    echo ""
    echo "========================================"
    echo " LAK-Agent 会话结束: $TIMESTAMP"
    echo "========================================"
    if [ -f "$SUMMARY_FILE" ] && [ -s "$SUMMARY_FILE" ]; then
      echo ""
      echo " 本次会话工具使用统计:"
      echo " ----------------------"
      while IFS=':' read -r tool count; do
        [ -n "$tool" ] && printf "   %-22s %s 次\n" "$tool" "$count"
      done < "$SUMMARY_FILE"
      echo ""
    else
      echo " (无工具调用记录)"
    fi
  } >> "$LOG_DIR/session.log"
  exit 0
fi

# =============================================================================
# PostToolUse 事件: 记录每次工具调用
# =============================================================================
if [ -n "$PYTHON" ]; then
  TOOL_NAME=$("$PYTHON" -c "
import sys, json
data = json.load(sys.stdin)
print(data.get('tool_name', ''))
" <<< "$PAYLOAD" 2>/dev/null)
else
  TOOL_NAME="${CLAUDE_TOOL_NAME:-unknown}"
fi

# 记录文件变更（Write / Edit）
if [ "$TOOL_NAME" = "Write" ] || [ "$TOOL_NAME" = "Edit" ]; then
  if [ -n "$PYTHON" ]; then
    FILE_PATH=$("$PYTHON" -c "
import sys, json
data = json.load(sys.stdin)
ti = data.get('tool_input', {})
print(ti.get('file_path', ''))
" <<< "$PAYLOAD" 2>/dev/null)
  else
    FILE_PATH="${CLAUDE_FILE_PATH:-unknown-file}"
  fi
  REL_PATH="${FILE_PATH#${CLAUDE_PROJECT_DIR}/}"
  REL_PATH="${REL_PATH#${CLAUDE_PROJECT_DIR}\\}"
  echo "[$TIMESTAMP] $TOOL_NAME -> $REL_PATH" >> "$LOG_DIR/file-changes.log"
fi

# 记录所有工具调用
if [ -n "$PYTHON" ]; then
  TOOL_OUTPUT=$("$PYTHON" -c "
import sys, json
data = json.load(sys.stdin)
resp = data.get('tool_response', {})
if isinstance(resp, dict):
    out = resp.get('stdout', resp.get('result', str(resp)))
elif isinstance(resp, str):
    out = resp
else:
    out = str(resp) if resp is not None else ''
print(out)
" <<< "$PAYLOAD" 2>/dev/null)
else
  TOOL_OUTPUT="${CLAUDE_TOOL_OUTPUT:-}"
fi

LINE_COUNT=$(printf '%s' "$TOOL_OUTPUT" | wc -l | tr -d ' ')
CHAR_COUNT=$(printf '%s' "$TOOL_OUTPUT" | wc -c | tr -d ' ')
echo "[$TIMESTAMP] $TOOL_NAME | ${LINE_COUNT}行 ${CHAR_COUNT}字符" >> "$LOG_DIR/tool-usage.log"

# 更新工具使用计数
SUMMARY_FILE="$LOG_DIR/tool-summary.txt"
if [ -f "$SUMMARY_FILE" ]; then
  if grep -q "^${TOOL_NAME}:" "$SUMMARY_FILE" 2>/dev/null; then
    COUNT=$(grep "^${TOOL_NAME}:" "$SUMMARY_FILE" | cut -d':' -f2)
    NEW_COUNT=$((COUNT + 1))
    grep -v "^${TOOL_NAME}:" "$SUMMARY_FILE" > "$SUMMARY_FILE.tmp" 2>/dev/null
    echo "${TOOL_NAME}:${NEW_COUNT}" >> "$SUMMARY_FILE.tmp"
    mv "$SUMMARY_FILE.tmp" "$SUMMARY_FILE"
  else
    echo "${TOOL_NAME}:1" >> "$SUMMARY_FILE"
  fi
else
  echo "${TOOL_NAME}:1" > "$SUMMARY_FILE"
fi

exit 0
