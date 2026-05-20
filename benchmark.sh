#!/usr/bin/env bash
# CodeLens 生产级基准测试脚本 v2
set -e

JAR="/c/workspace/codelens-java/codelens-cli/target/codelens-0.1.0.jar"
API_KEY="${CODELENS_API_KEY:-}"
if [ -z "$API_KEY" ]; then echo "ERROR: CODELENS_API_KEY not set"; exit 1; fi
OUTDIR="/c/workspace/codelens-java/target/benchmark"
mkdir -p "$OUTDIR"

RESULTS_CSV="$OUTDIR/results.csv"
SUMMARY_FILE="$OUTDIR/summary.txt"

# Clean previous results
rm -f "$RESULTS_CSV"
echo "file|category|lines|total_ms|llm_ok|l1_confidence|l1_passed|l1_total|l1_rate_pct|l1_issues" > "$RESULTS_CSV"

run_test() {
  local category="$1"
  local lines="$2"
  local filepath="$3"
  local filename=$(basename "$filepath")
  local safe_name="${filename%.java}"

  echo "▶ [$category/${lines}行] $filename"

  # Measure wall-clock total time
  local start_total=$(date +%s%N)

  # Run with --no-cache to force fresh LLM calls, --no-color for clean output
  local output
  output=$(CODELENS_API_KEY="$API_KEY" java -jar "$JAR" analyze "$filepath" --no-cache --no-color 2>&1) || true

  local end_total=$(date +%s%N)
  local total_ms=$(( (end_total - start_total) / 1000000 ))

  # Save full raw output for auditing
  echo "$output" > "$OUTDIR/${safe_name}_output.txt"

  # Check for API error
  local llm_ok="YES"
  if echo "$output" | grep -q "API 调用失败"; then
    llm_ok="NO"
  fi

  # --- Parse L1 validation results ---
  # L1 section: "校验结果: [OK] CERTAIN (X/X 通过)"
  # The Chinese chars are garbled in output, so we use [OK]/[XX] etc as anchors
  local l1_confidence="N/A"
  local l1_passed=0
  local l1_total=0
  local l1_rate_pct="N/A"
  local l1_issues="-"

  # Find L1 result line: contains "[OK] CERTAIN" or similar, with "(X/X" pattern
  local l1_result_line
  l1_result_line=$(echo "$output" | grep -E '\[(OK|!!|!|XX)\].*\([0-9]+/[0-9]+' | head -1)

  if [ -n "$l1_result_line" ]; then
    # Extract "[OK]" etc
    l1_confidence=$(echo "$l1_result_line" | grep -o '\[OK\]\|\[!!\]\|\[!\]\|\[XX\]' | head -1)
    # Extract "3/3" from "(3/3" context
    local paren_match
    paren_match=$(echo "$l1_result_line" | grep -o '([0-9][0-9]*/[0-9][0-9]*' | head -1 | tr -d '()')
    if [ -n "$paren_match" ]; then
      l1_passed=$(echo "$paren_match" | cut -d/ -f1)
      l1_total=$(echo "$paren_match" | cut -d/ -f2)
      if [ "$l1_total" -gt 0 ] 2>/dev/null; then
        l1_rate_pct=$(( l1_passed * 100 / l1_total ))
      fi
    fi

    # Count issue lines in L1 section (lines between L1 header and L2 header)
    # Strategy: extract output from first "[OK]/[XX] CERTAIN/LOW" until "L2" with similar pattern
    local l1_section
    l1_section=$(echo "$output" | awk 'BEGIN{flag=0} /\[(OK|!!|!|XX)\].*\([0-9]+\/[0-9]+/{if(flag==0){flag=1;next}} /\[(OK|!!|!|XX)\].*\([0-9]+\/[0-9]+/{if(flag==1) exit} flag')
    l1_issues=$(echo "$l1_section" | grep -c '^\[\(XX\|!!\|!\)\]' || true)
  fi

  # --- Log to CSV ---
  echo "${filename}|${category}|${lines}|${total_ms}|${llm_ok}|${l1_confidence:-N/A}|${l1_passed:-0}|${l1_total:-0}|${l1_rate_pct:-N/A}|${l1_issues:-0}" >> "$RESULTS_CSV"

  # --- Print summary line ---
  if [ "$llm_ok" = "YES" ]; then
    echo "  ✓ ${total_ms}ms | L1: ${l1_confidence:-?} (${l1_passed:-0}/${l1_total:-0}) | $filename"
  else
    echo "  ✗ ${total_ms}ms | API_ERROR | $filename"
  fi
}

echo ""
echo "╔══════════════════════════════════════════════════════╗"
echo "║   CodeLens 生产级基准测试                            ║"
echo "║   模型: deepseek-v4-flash                           ║"
echo "║   时间: $(date)                                     ║"
echo "╚══════════════════════════════════════════════════════╝"
echo ""

run_test "SMALL"   "16"  "/c/workspace/fmp/FMP-INTERFACE/src/main/java/com/stream/interfaces/demo/service/impl/HelloServiceImpl.java"
run_test "SMALL"   "19"  "/c/workspace/fmp/FMP-SCS/src/main/java/com/stream/scs/freeze/service/impl/ScsFreezeServiceImpl.java"
run_test "MEDIUM"  "62"  "/c/workspace/fmp/FMP-BMS/src/main/java/com/stream/bms/prepara/service/impl/BmsPreparaItemImportServiceImpl.java"
run_test "MEDIUM"  "85"  "/c/workspace/fmp/FMP-AMS/src/main/java/com/stream/ams/bill/service/impl/AmsBillMainServiceImpl.java"
run_test "MEDIUM"  "97"  "/c/workspace/fmp/FMP-CONTRACT/src/main/java/com/stream/contract/bill/service/impl/ContractManageChangeServiceImpl.java"
run_test "LARGE"   "210" "/c/workspace/fmp/FMP-BILL/src/main/java/com/stream/bill/service/impl/BillTemplateInfoServiceImpl.java"
run_test "LARGE"   "212" "/c/workspace/fmp/FMP-INTERFACE/src/main/java/com/stream/interfaces/kingdee/service/impl/KingdeeDataServiceImpl.java"

echo ""

# Generate Python-free summary
{
  echo "=============================================="
  echo "  CodeLens 基准测试报告"
  echo "=============================================="
  echo "  测试时间 : $(date)"
  echo "  CLI版本  : 0.1.0"
  echo "  模型     : deepseek-v4-flash"
  echo "  温度     : 0.1"
  echo "  最大Token: 8192"
  echo "  测试文件 : 7 个 (small=2, medium=3, large=2)"
  echo "=============================================="
  echo ""
  echo "--- 逐文件结果 ---"

  # Calculate aggregates
  TOTAL_LLM_OK=0
  TOTAL_TIME=0
  TIMES=()
  L1_TOTAL_ALL=0
  L1_PASSED_ALL=0

  while IFS='|' read -r f cat lines total llm_ok l1_conf l1_pass l1_tot l1_rate l1_issues; do
    if [ "$f" = "file" ]; then continue; fi
    TOTAL_TIME=$((TOTAL_TIME + total))
    TIMES+=("$total")
    if [ "$llm_ok" = "YES" ]; then
      TOTAL_LLM_OK=$((TOTAL_LLM_OK + 1))
      L1_TOTAL_ALL=$((L1_TOTAL_ALL + l1_tot))
      L1_PASSED_ALL=$((L1_PASSED_ALL + l1_pass))
    fi
    echo "  $f : ${total}ms | L1=${l1_conf} (${l1_pass}/${l1_tot}) | LLM=${llm_ok}"
  done < "$RESULTS_CSV"

  FILE_COUNT=7
  echo ""
  echo "--- 聚合统计 ---"
  echo "  文件总数        : $FILE_COUNT"
  echo "  LLM成功         : $TOTAL_LLM_OK"
  echo "  LLM失败         : $((FILE_COUNT - TOTAL_LLM_OK))"
  echo "  LLM成功率       : $((TOTAL_LLM_OK * 100 / FILE_COUNT))%"
  echo ""

  # Sort times for percentile
  IFS=$'\n' SORTED_TIMES=($(sort -n <<<"${TIMES[*]}"))
  unset IFS

  SUM_TIME=0
  for t in "${SORTED_TIMES[@]}"; do SUM_TIME=$((SUM_TIME + t)); done
  AVG_TIME=$((SUM_TIME / FILE_COUNT))
  MIN_TIME="${SORTED_TIMES[0]}"
  MAX_TIME="${SORTED_TIMES[$((FILE_COUNT - 1))]}"
  MEDIAN_TIME="${SORTED_TIMES[$((FILE_COUNT / 2))]}"

  echo "  总耗时分布 (ms):"
  echo "    最小值  : $MIN_TIME"
  echo "    最大值  : $MAX_TIME"
  echo "    平均值  : $AVG_TIME"
  echo "    中位数  : $MEDIAN_TIME"

  # By category
  for cat in "SMALL" "MEDIUM" "LARGE"; do
    CAT_TIMES=()
    CAT_OK=0
    CAT_L1_PASS=0
    CAT_L1_TOTAL=0
    while IFS='|' read -r f cat2 lines total llm_ok l1_conf l1_pass l1_tot l1_rate l1_issues; do
      if [ "$f" = "file" ]; then continue; fi
      if [ "$cat2" = "$cat" ]; then
        CAT_TIMES+=("$total")
        if [ "$llm_ok" = "YES" ]; then
          CAT_OK=$((CAT_OK + 1))
          CAT_L1_PASS=$((CAT_L1_PASS + l1_pass))
          CAT_L1_TOTAL=$((CAT_L1_TOTAL + l1_tot))
        fi
      fi
    done < "$RESULTS_CSV"

    if [ ${#CAT_TIMES[@]} -gt 0 ]; then
      CAT_SUM=0
      for t in "${CAT_TIMES[@]}"; do CAT_SUM=$((CAT_SUM + t)); done
      CAT_AVG=$((CAT_SUM / ${#CAT_TIMES[@]}))
      echo ""
      echo "  [$cat] (${#CAT_TIMES[@]} files):"
      echo "    平均耗时      : ${CAT_AVG}ms"
      echo "    LLM成功率     : $((CAT_OK * 100 / ${#CAT_TIMES[@]}))%"
      if [ "$CAT_L1_TOTAL" -gt 0 ]; then
        echo "    L1通过率      : $((CAT_L1_PASS * 100 / CAT_L1_TOTAL))% ($CAT_L1_PASS/$CAT_L1_TOTAL)"
      fi
    fi
  done

  echo ""
  echo "--- L1 证据校验汇总 ---"
  if [ "$L1_TOTAL_ALL" -gt 0 ]; then
    echo "  总校验点    : $L1_TOTAL_ALL"
    echo "  总通过数    : $L1_PASSED_ALL"
    echo "  总通过率    : $((L1_PASSED_ALL * 100 / L1_TOTAL_ALL))%"
  else
    echo "  无结构化校验点（纯文本结论模式）"
  fi

  # Confidence distribution
  echo ""
  echo "--- L1 置信度分布 ---"
  L1_CERTAIN=0; L1_HIGH=0; L1_MEDIUM=0; L1_LOW=0
  while IFS='|' read -r f cat lines total llm_ok l1_conf l1_pass l1_tot l1_rate l1_issues; do
    if [ "$f" = "file" ]; then continue; fi
    case "$l1_conf" in
      "[OK]") L1_CERTAIN=$((L1_CERTAIN+1)) ;;
      "[!!]") L1_HIGH=$((L1_HIGH+1)) ;;
      "[!]") L1_MEDIUM=$((L1_MEDIUM+1)) ;;
      "[XX]") L1_LOW=$((L1_LOW+1)) ;;
    esac
  done < "$RESULTS_CSV"
  echo "  CERTAIN : $L1_CERTAIN"
  echo "  HIGH    : $L1_HIGH"
  echo "  MEDIUM  : $L1_MEDIUM"
  echo "  LOW     : $L1_LOW"

} > "$SUMMARY_FILE"

cat "$SUMMARY_FILE"
echo ""
echo "══════════════════════════════════════════════════════"
echo "  原始输出: $OUTDIR"
echo "  CSV结果:  $RESULTS_CSV"
echo "  摘要:     $SUMMARY_FILE"
echo "══════════════════════════════════════════════════════"
