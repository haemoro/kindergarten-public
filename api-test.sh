#!/bin/bash

# Kindergarten API Speed Test Script
# Server: localhost:1025

BASE_URL="http://localhost:1025"
TOTAL_TIME=0
COUNT=0

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

# Curl format string: status_code|total_time_ms|ttfb_ms
CURL_FORMAT='%{http_code}|%{time_total}|%{time_starttransfer}'

colorize_time() {
    local ms=$1
    local int_ms=${ms%.*}
    if [ "$int_ms" -lt 500 ]; then
        echo -e "${GREEN}${ms}ms${NC}"
    elif [ "$int_ms" -lt 1000 ]; then
        echo -e "${YELLOW}${ms}ms${NC}"
    else
        echo -e "${RED}${ms}ms${NC}"
    fi
}

to_ms() {
    echo "$1" | awk '{printf "%.0f", $1 * 1000}'
}

print_separator() {
    printf '%0.s-' {1..90}
    echo
}

run_test() {
    local name="$1"
    local url="$2"

    local result
    result=$(curl -s -o /dev/null -w "$CURL_FORMAT" "$url" 2>/dev/null)

    local status=$(echo "$result" | cut -d'|' -f1)
    local total_sec=$(echo "$result" | cut -d'|' -f2)
    local ttfb_sec=$(echo "$result" | cut -d'|' -f3)

    local total_ms=$(to_ms "$total_sec")
    local ttfb_ms=$(to_ms "$ttfb_sec")

    TOTAL_TIME=$((TOTAL_TIME + total_ms))
    COUNT=$((COUNT + 1))

    local colored_total=$(colorize_time "$total_ms")
    local colored_ttfb=$(colorize_time "$ttfb_ms")

    printf "  %-36s  %s  %12s  %12s\n" \
        "$name" \
        "$([ "$status" = "200" ] && echo -e "${GREEN}${status}${NC}" || echo -e "${RED}${status}${NC}")" \
        "$(colorize_time "$total_ms")" \
        "$(colorize_time "$ttfb_ms")"
}

get_first_id() {
    local response
    response=$(curl -s "${BASE_URL}/api/app/kindergartens/search?lat=37.5665&lng=126.978&radiusKm=5&size=1" 2>/dev/null)
    echo "$response" | python3 -c "
import sys, json
try:
    data = json.load(sys.stdin)
    if 'content' in data and len(data['content']) > 0:
        print(data['content'][0]['id'])
    elif isinstance(data, list) and len(data) > 0:
        print(data[0]['id'])
    else:
        print('')
except:
    print('')
" 2>/dev/null
}

# ============================================================
# Main
# ============================================================

echo
echo -e "${BOLD}${CYAN}  Kindergarten API Speed Test${NC}"
echo -e "  Server: ${BASE_URL}"
echo -e "  Date:   $(date '+%Y-%m-%d %H:%M:%S')"
echo
print_separator
printf "  ${BOLD}%-36s  %-6s  %12s  %12s${NC}\n" "Endpoint" "Status" "Total" "TTFB"
print_separator

# 1. Location-based search
run_test "1. 목록 검색 (위치 기반)" \
    "${BASE_URL}/api/app/kindergartens/search?lat=37.5665&lng=126.978&radiusKm=5&size=20"

# 2. Text search
run_test "2. 목록 검색 (텍스트)" \
    "${BASE_URL}/api/app/kindergartens/search?query=%EC%84%9C%EC%9A%B8&size=20"

# 3. Region filter search
run_test "3. 목록 검색 (지역필터)" \
    "${BASE_URL}/api/app/kindergartens/search?sidoCode=11&size=20"

# 4. Detail - get an ID first
echo -ne "  ${BOLD}>> ID 조회 중...${NC}\r"
KINDERGARTEN_ID=$(get_first_id)
echo -ne "                          \r"

if [ -n "$KINDERGARTEN_ID" ] && [ "$KINDERGARTEN_ID" != "" ]; then
    run_test "4. 상세 조회 (${KINDERGARTEN_ID:0:8}...)" \
        "${BASE_URL}/api/app/kindergartens/${KINDERGARTEN_ID}"
else
    printf "  %-36s  ${RED}%-6s${NC}  %12s  %12s\n" \
        "4. 상세 조회" "SKIP" "-" "-"
    echo -e "     ${YELLOW}(검색 결과에서 ID를 가져올 수 없습니다)${NC}"
fi

# 5. Map markers
run_test "5. 지도 마커" \
    "${BASE_URL}/api/app/kindergartens/map-markers?lat=37.5665&lng=126.978&radiusKm=3"

print_separator

# Summary
if [ "$COUNT" -gt 0 ]; then
    AVG=$((TOTAL_TIME / COUNT))
    AVG_COLOR=$(colorize_time "$AVG")
    echo
    echo -e "  ${BOLD}Summary${NC}"
    echo -e "  Total requests:       ${COUNT}"
    echo -e "  Total time:           ${TOTAL_TIME}ms"
    echo -e "  Average response:     ${AVG_COLOR}"
    echo
else
    echo
    echo -e "  ${RED}No successful requests.${NC}"
    echo -e "  ${YELLOW}Is the server running at ${BASE_URL}?${NC}"
    echo
fi
