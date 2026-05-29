#!/bin/bash
# CodeLens CLI 基准测试对比 - single vs multi mode
# Usage: ./run_benchmark_compare.sh

set -e

API_KEY="${CODELENS_API_KEY:-sk-84b4e410881c4208aba42a5901e5889b}"
JAVA="$(which java)"
JAR="codelens-cli/target/codelens-0.4.0.jar"

OUTDIR_BASE="benchmark-results"
SINGLE_DIR="$OUTDIR_BASE/single"
MULTI_DIR="$OUTDIR_BASE/multi"

# 清理上一次结果 + 缓存，确保 --no-cache 生效
rm -rf "$SINGLE_DIR" "$MULTI_DIR"
mkdir -p "$SINGLE_DIR" "$MULTI_DIR"
rm -rf .codelens/granular/

# Test cases from Round 4
CASES=(
  "C1|/mnt/c/workspace/fmp/FMP-ECS/src/main/java/com/stream/ecs/bill/handler/EcsBillDataSaveHandler.java"
  "C2|/mnt/c/workspace/fmp/FMP-TRAVEL/FMP-TRAVEL-SETTLE/src/main/java/com/stream/travel/settle/handler/CreateTravelSettleAccountsHandler.java"
  "C3|/mnt/c/workspace/fmp/FMP-WEB/src/main/java/com/stream/fmp/controller/SysDimenController.java"
  "C4|/mnt/c/workspace/fmp/FMP-WEB/src/main/java/com/stream/fmp/controller/LoginController.java"
  "C5|/mnt/c/workspace/fmp/FMP-WEB/src/main/java/com/stream/fmp/controller/CommonBillInfoController.java"
  "C6|/mnt/c/workspace/fmp/FMP-AMS/src/main/java/com/stream/ams/bill/handler/AmsBillDataSaveHandler.java"
  "C7|/mnt/c/workspace/fmp/FMP-TRAVEL/FMP-TRAVEL-SETTLE/src/main/java/com/stream/travel/settle/ibo/model/PaymentResponse.java"
  "C8|/mnt/c/Users/dj/Downloads/ProcurementApprovalServiceImpl.java"
  "C9|/mnt/c/workspace/codelens-java/codelens-cli/src/test/resources/SysUserServiceImpl.java"
  "C10|/mnt/c/workspace/fmp/FMP-INTERFACE/src/main/java/com/stream/interfaces/kingdee/service/impl/KingdeeDataServiceImpl.java"
)

echo "=============================================="
echo " CodeLens Benchmark: single vs multi comparison"
echo " Date: $(date)"
echo " JAR: codelens-0.4.0"
echo "=============================================="

OVERALL_START=$(date +%s)

for MODE in "single" "multi"; do
  MODE_FLAG=""
  [ "$MODE" = "multi" ] && MODE_FLAG="--mode=multi"

  echo ""
  echo "========== MODE: $MODE =========="

  MODE_START=$(date +%s)

  for CASE in "${CASES[@]}"; do
    LABEL="${CASE%%|*}"
    FILEPATH="${CASE##*|}"

    # C7 is a pure BO class, skip as usual
    if [ "$LABEL" = "C7" ]; then
      echo "  $LABEL: skip (pure BO)"
      continue
    fi

    FILE_WIN=$(wslpath -w "$FILEPATH" 2>/dev/null || echo "$FILEPATH")
    OUTFILE="benchmark-results/${MODE}/${LABEL}_output.log"

    echo ""
    echo "  [$LABEL] $(basename $FILEPATH)"

    START_TIME=$(date +%s)

    timeout 300 "$JAVA" -jar "$JAR" analyze "$FILE_WIN" "$API_KEY" --no-cache $MODE_FLAG 2>&1 | tee "$OUTFILE" || true

    END_TIME=$(date +%s)
    ELAPSED=$((END_TIME - START_TIME))
    echo "  [$LABEL] done (${ELAPSED}s)"
  done

  MODE_END=$(date +%s)
  echo "  MODE $MODE total: $((MODE_END - MODE_START))s"
done

OVERALL_END=$(date +%s)

# Generate comparison report
REPORT="$OUTDIR_BASE/single_vs_multi_report.md"

{
  echo "# CodeLens Benchmark: single vs multi comparison"
  echo ""
  echo "**Date**: $(date)"
  echo "**JAR**: codelens-0.4.0"
  echo "**Model**: deepseek-v4-flash"
  echo ""
  echo "| Case | Single time | Multi time | Single size | Multi size | Match |"
  echo "|------|-------------|------------|-------------|------------|-------|"
} > "$REPORT"

for CASE in "${CASES[@]}"; do
  LABEL="${CASE%%|*}"
  [ "$LABEL" = "C7" ] && continue

  S_FILE="benchmark-results/single/${LABEL}_output.log"
  M_FILE="benchmark-results/multi/${LABEL}_output.log"

  S_TIME="?"
  M_TIME="?"
  if [ -f "$S_FILE" ]; then
    S_LINE=$(grep -oP 'done \(\K(\d+)' "$S_FILE" 2>/dev/null | tail -1 || true)
    [ -n "$S_LINE" ] && S_TIME="${S_LINE}s"
  fi
  if [ -f "$M_FILE" ]; then
    M_LINE=$(grep -oP 'done \(\K(\d+)' "$M_FILE" 2>/dev/null | tail -1 || true)
    [ -n "$M_LINE" ] && M_TIME="${M_LINE}s"
  fi

  S_SIZE=$(stat -c%s "$S_FILE" 2>/dev/null || echo 0)
  M_SIZE=$(stat -c%s "$M_FILE" 2>/dev/null || echo 0)
  S_SIZE_KB=$((S_SIZE / 1024))
  M_SIZE_KB=$((M_SIZE / 1024))

  # Simple match check: compare JSON structure lines
  MATCH="N/A"
  if [ -f "$S_FILE" ] && [ -f "$M_FILE" ]; then
    S_JSON=$(grep -oP '(\{.+\}|\[.+\])' "$S_FILE" | head -1 || true)
    M_JSON=$(grep -oP '(\{.+\}|\[.+\])' "$M_FILE" | head -1 || true)
    if [ -n "$S_JSON" ] && [ -n "$M_JSON" ]; then
      # Compare method count
      S_CNT=$(echo "$S_JSON" | grep -o '"name"' | wc -l)
      M_CNT=$(echo "$M_JSON" | grep -o '"name"' | wc -l)
      if [ "$S_CNT" = "$M_CNT" ]; then
        MATCH="methods:${S_CNT}"
      else
        MATCH="S:${S_CNT}vsM:${M_CNT}"
      fi
    fi
  fi

  echo "| $LABEL | $S_TIME | $M_TIME | ${S_SIZE_KB}KB | ${M_SIZE_KB}KB | $MATCH |" >> "$REPORT"
done

echo "" >> "$REPORT"
echo "## 原始输出" >> "$REPORT"
echo "" >> "$REPORT"
echo "- [single outputs](single/)" >> "$REPORT"
echo "- [multi outputs](multi/)" >> "$REPORT"
echo "" >> "$REPORT"
echo "总耗时: $((OVERALL_END - OVERALL_START))s" >> "$REPORT"

echo ""
echo "=============================================="
echo " Done! $((OVERALL_END - OVERALL_START))s total"
echo " Report: $REPORT"
echo "=============================================="
cat "$REPORT"
