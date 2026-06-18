#!/usr/bin/env bash
# =============================================================================
# Hook: PreToolUse (matcher: "Bash")
# 用途: 在 Bash 命令执行前拦截危险操作
# 三级拦截: Lv1 数据库高危(阻止) Lv2 敏感信息泄露(阻止) Lv3 代码质量(警告)
# 适配: LAK-Agent 政法智能知识Agent平台
# =============================================================================

LOG_DIR="${CLAUDE_PROJECT_DIR}/.claude/hook-logs"
mkdir -p "$LOG_DIR"

PAYLOAD=$(cat)

# ----- 解析 payload 提取命令 -----
PYTHON=""
if command -v python &>/dev/null; then
  PYTHON="python"
elif command -v python3 &>/dev/null; then
  PYTHON="python3"
fi

if [ -n "$PYTHON" ]; then
  USER_COMMAND=$("$PYTHON" -c "
import sys, json
data = json.load(sys.stdin)
ti = data.get('tool_input', {})
print(ti.get('command', ''))
" <<< "$PAYLOAD" 2>/dev/null)
else
  USER_COMMAND="${CLAUDE_TOOL_INPUT:-}"
fi

TIMESTAMP=$(date '+%Y-%m-%d %H:%M:%S')

# =============================================================================
# Level 1: 数据库高危操作 — 匹配到直接阻止
# =============================================================================
LV1_PATTERNS=(
  "DROP\s+(TABLE|DATABASE|INDEX)"
  "TRUNCATE\s+(TABLE\s+)?"
  "DELETE\s+FROM\s+\w+\s*[^W]"            # DELETE FROM 但后面没有 WHERE
  "DELETE\s+FROM\s+\w+\s*$"              # DELETE FROM 行尾
  "DELETE\s+FROM\s+\w+\s+WHERE\s+(1\s*=\s*1|true)\b"  # DELETE ... WHERE 1=1 绕过
  "DELETE\s+FROM\s+audit_log"            # 审计日志禁止删除
  "ALTER\s+TABLE\s+\w+\s+DROP\s+COLUMN"
  "UPDATE\s+\w+\s+SET\s+.*[^W]$"         # UPDATE 无 WHERE
  "UPDATE\s+\w+\s+SET\s+.*WHERE\s+(1\s*=\s*1|true)\b"  # UPDATE ... WHERE 1=1 绕过
  "RENAME\s+TABLE"
  "GRANT\s+ALL"                          # 禁止 GRANT ALL
)

for pattern in "${LV1_PATTERNS[@]}"; do
  if echo "$USER_COMMAND" | grep -Eiq "$pattern"; then
    echo "[$TIMESTAMP] LV1-BLOCKED: $USER_COMMAND (匹配: $pattern)" >> "$LOG_DIR/blocked-commands.log"
    echo "==============================================" >&2
    echo " [安全拦截 - Level 1] 检测到高危数据库操作!" >&2
    echo " 匹配模式: $pattern" >&2
    echo " 被拦截命令: $USER_COMMAND" >&2
    echo " 高危 DDL/DML 必须人工审核后执行" >&2
    echo " 审计日志表(audit_log)禁止物理删除，仅允许 INSERT+SELECT" >&2
    echo " 日志: $LOG_DIR/blocked-commands.log" >&2
    echo "==============================================" >&2
    exit 1
  fi
done

# =============================================================================
# Level 2: 敏感信息泄露 — 匹配到阻止
# =============================================================================
LV2_PATTERNS=(
  "password\s*=\s*[\"'][^\"']+[\"']"       # hardcoded password
  "passwd\s*=\s*[\"'][^\"']+[\"']"
  "secretKey\s*=\s*[\"'][^\"']+[\"']"
  "accessKey\s*=\s*[\"'][^\"']+[\"']"
  "apiKey\s*=\s*[\"'][^\"']+[\"']"
  "DASHSCOPE_API_KEY\s*=\s*[\"'][^\"']+[\"']"   # 百炼 API Key
  "JWT_SECRET\s*=\s*[\"'][^\"']+[\"']"           # JWT 密钥
  "jdbc:\w+://[^/]+/[?].*password="              # JDBC URL with password
  "BCRYPT_PASSWORD_HASH"                          # 防止写死测试密码
)

for pattern in "${LV2_PATTERNS[@]}"; do
  if echo "$USER_COMMAND" | grep -Eiq "$pattern"; then
    echo "[$TIMESTAMP] LV2-BLOCKED: $USER_COMMAND (匹配: $pattern)" >> "$LOG_DIR/blocked-commands.log"
    echo "==============================================" >&2
    echo " [安全拦截 - Level 2] 检测到敏感信息硬编码!" >&2
    echo " 匹配模式: $pattern" >&2
    echo " 被拦截命令: $USER_COMMAND" >&2
    echo " 密钥/密码必须通过环境变量注入，禁止硬编码在代码或配置文件中" >&2
    echo " 日志: $LOG_DIR/blocked-commands.log" >&2
    echo "==============================================" >&2
    exit 1
  fi
done

# =============================================================================
# Level 3: 代码质量 — 匹配到警告但不阻止（LAK-Agent 定制）
# =============================================================================
LV3_PATTERNS=(
  "for\s*\(.*\)\s*\{[^}]*Mapper\."      # N+1: 循环内调 Mapper
  "System\.out\.println"                # 违规日志
  "new\s+Thread\s*\(\s*\)\s*\.start"   # 手动 new Thread
  "catch\s*\(.*Exception.*\)\s*\{\s*\}" # 空 catch 块
  "\.printStackTrace\(\)"               # 禁止 printStackTrace
  "String\.format.*password"            # 日志中打印密码
  "log\.info.*password"                 # log 中打印密码
  "setAttribute.*password"              # response 中写密码
)

for pattern in "${LV3_PATTERNS[@]}"; do
  if echo "$USER_COMMAND" | grep -Eiq "$pattern"; then
    echo "[$TIMESTAMP] LV3-WARNING: $USER_COMMAND (匹配: $pattern)" >> "$LOG_DIR/blocked-commands.log"
    echo "==============================================" >&2
    echo " [代码质量警告 - Level 3] 检测到不符合阿里巴巴Java规范写法!" >&2
    echo " 匹配模式: $pattern" >&2
    echo " 命令内容: $USER_COMMAND" >&2
    echo " 建议: 参考 CLAUDE.md 编码规范章节修正" >&2
    echo "==============================================" >&2
  fi
done

exit 0
