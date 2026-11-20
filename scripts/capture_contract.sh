#!/usr/bin/env bash
# 앱·서버 연결 검사(ServerContractTest)에 쓰는 서버 응답을 실제 서버에서 받아 저장한다.
#
#   scripts/capture_contract.sh                      # 운영 서버 → app/src/test/resources/contract/
#   scripts/capture_contract.sh https://다른서버 폴더   # 다른 서버, 다른 폴더
#
# - 앱이 실제로 부르는 조회 주소만 부른다. DB에 쓰는 주소(신고 올리기, 내 정보, 이동 기록, 걸음 속도)와
#   제미나이를 부르는 주소(/api/assistant, /api/ai/*)는 부르지 않는다.
# - /api/transit 은 서버의 ODsay 하루 호출 수를 1 올린다 (하루 한 번 검사라 괜찮음).
# - 위치는 시연 지역(국립아시아문화전당 둘레)으로 고정한다.
# - 한 곳이라도 200이 아니거나 JSON이 아니면 실패한다 (앞서 저장한 파일은 그대로 둔다).
set -euo pipefail

BASE="${1:-${SERVER_BASE_URL:-https://safe-route.java21.net}}"
OUT="${2:-app/src/test/resources/contract}"
LAT=35.1467
LON=126.9196
BBOX="126.9160,35.1440,126.9230,35.1495"   # 지도 시설: 좁게 (응답이 커서)
WIDE="126.9100,35.1400,126.9300,35.1530"   # 행사·신고: 시연 지역 전체 (서석동 급경사 신고 포함)
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

fetch() { # 이름 curl-인자...
  local name="$1"
  shift
  local code
  code=$(curl -sS -m 60 -o "$TMP/$name.json" -w '%{http_code}' "$@")
  if [ "$code" != "200" ]; then
    echo "::error::$name: HTTP $code $(head -c 300 "$TMP/$name.json")"
    return 1
  fi
  python3 -m json.tool "$TMP/$name.json" > "$TMP/$name.pretty" || { echo "::error::$name: JSON이 아니에요"; return 1; }
  mv "$TMP/$name.pretty" "$TMP/$name.json"
  echo "$name: $(wc -c < "$TMP/$name.json" | tr -d ' ') bytes"
}

post() { # 이름 경로 본문
  fetch "$1" -H 'Content-Type: application/json' -d "$3" "$BASE$2"
}

get() { # 이름 경로 (나머지는 curl -G --data-urlencode 인자)
  local name="$1" path="$2"
  shift 2
  local args=()
  for kv in "$@"; do args+=(--data-urlencode "$kv"); done
  fetch "$name" -G "${args[@]}" "$BASE$path"
}

# 문화전당역 5번 출구 쪽 → 국립아시아문화전당, 휠체어 (앱 RouteRequest.toJson 과 같은 모양)
post route /api/route \
  '{"origin":{"lat":35.1491,"lon":126.9203},"destination":{"lat":35.1467,"lon":126.9196,"name":"국립아시아문화전당"},"mode":"wheelchair","night":"auto"}'
# 동부소방서 쪽 → 양림동 (앱 TransitRepository 와 같은 모양)
post transit /api/transit \
  '{"origin":{"lat":35.1543,"lon":126.9142},"destination":{"lat":35.1405,"lon":126.9134},"mode":"adult"}'
get bus /api/bus/nearby "lat=$LAT" "lon=$LON"
get subway /api/subway/nearby "lat=$LAT" "lon=$LON"
get events /api/events "bbox=$WIDE"
get reports /api/reports "bbox=$WIDE"
get reports_active /api/reports/active "bbox=$WIDE"
# 휠체어 모드 지도에 그리는 종류 + 모든 모드 공통 (앱 serverKinds)
get pois /api/pois "bbox=$BBOX" "kinds=bell,cctv,crosswalk,footbridge,police,shelter,stairs,store,subway,subway_entrance,toilet" "limit=200"
get places_here /api/places/here "lat=$LAT" "lon=$LON"
get places /api/places "query=문화전당" "lat=$LAT" "lon=$LON"
get places_nearby /api/places/nearby "lat=$LAT" "lon=$LON" "kind=toilet" "mode=wheelchair"

mkdir -p "$OUT"
cp "$TMP"/*.json "$OUT"/
echo "저장: $OUT ($(date '+%Y-%m-%d %H:%M'), $BASE)"
