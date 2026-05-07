#!/bin/bash

GREEN='\033[0;32m'
YELLOW='\033[0;33m'
NC='\033[0m'

echo -e "${GREEN}🛑 RushDeal MSA 로컬 환경 종료를 시작합니다...${NC}"

docker-compose -f docker-compose-app.yml down

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ 모든 컨테이너가 정상 종료되었습니다.${NC}"
    echo -e "${YELLOW}💡 볼륨(데이터)까지 초기화하려면: docker-compose -f docker-compose-app.yml down -v${NC}"
else
    echo -e "\033[0;31m❌ 종료 중 문제가 발생했습니다.\033[0m"
    exit 1
fi
