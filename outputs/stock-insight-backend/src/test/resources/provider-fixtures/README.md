# Provider 계약 테스트 자료

이 폴더의 JSON·XML은 공식 개발가이드의 필드 형태에 맞춰 직접 만든 **가상 계약 테스트 자료**입니다.
실제 API 응답을 다운로드한 샘플이나 실제 기업의 검증된 수치가 아닙니다.
종목 123456, 고유번호 00123456, 회사명 ‘계약검증제조’와 금액은 테스트용입니다.

기본 수치의 독립 기대값은 기존 metric-expectations.csv의 NORMAL과 같습니다.
매출 1,200 / 영업이익 180 / 순이익 120 / 지배주주 순이익 90 / 지배주주 지분 600 /
자산 1,500 / 부채 700 / 자본 800 / 공시 EPS 30 / 종가 90 / 유통주식수 90입니다.
전기 숫자 999,999는 당기 원본 대신 사용되지 않는지 확인하기 위한 값입니다.
수집 실제 검증은 인증키 발급 후 별도로 수행하고, 키·URL·헤더를 제거한 응답만 보관해야 합니다.

명세 출처:
- https://opendart.fss.or.kr/guide/detail.do?apiGrpCd=DS003&apiId=2019020
- https://opendart.fss.or.kr/guide/detail.do?apiGrpCd=DS003&apiId=2019016
- https://opendart.fss.or.kr/guide/detail.do?apiGrpCd=DS002&apiId=2020002
- https://www.data.go.kr/data/15094808/openapi.do
