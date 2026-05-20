#!/bin/bash
# Создаёт тестовый граф "Дозволенность Мавлида" - один центральный CLAIM,
# вокруг него ARGUMENT за/против с EVIDENCE-обоснованиями. Используются
# все 5 типов рёбер: SUPPORTS, REFUTES, INVALIDATES, QUALIFIES, RESPONDS_TO.
#
# Идемпотентен: каждый запуск создаёт НОВУЮ тему (uuid'ы новые).
# Используется как regression-визуал для UI-проверок графа.
set -e
# DEV_USER_ID из DevUserSeeder.java - admin@argumentmap.local (ADMIN, bypass RBAC).
# Активен в profile local/dev/test через XUserIdAuthenticationFilter (ADR-040).
USER="00000000-0000-0000-0000-000000000001"
H_CT="Content-Type: application/json"
H_USR="X-User-Id: $USER"

# curl --fail-with-body даёт non-zero exit на HTTP 4xx/5xx + печатает тело
# в stderr - под set -e скрипт сразу остановится с понятным сообщением
# вместо тихого парсинга error-JSON в python и невнятного KeyError
CURL="curl -sS --fail-with-body"

post_topic() {
  $CURL -X POST http://localhost:9090/api/v1/topics \
    -H "$H_CT" -H "$H_USR" -d "$1"
}

post_node() {
  $CURL -X POST http://localhost:9090/api/v1/nodes \
    -H "$H_CT" -H "$H_USR" -d "$1" | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])"
}

post_edge() {
  $CURL -o /dev/null -w "%{http_code} " -X POST http://localhost:9090/api/v1/edges \
    -H "$H_CT" -H "$H_USR" -d "$1"
}

mknode() {
  # originalLang='ru' явно: текст содержит лигатуру ﷺ, без явного lang
  # фронт раньше детектил его как арабский (font-naskh на весь блок).
  python3 -c "import json,sys; print(json.dumps({'topicId':'$TOPIC_ID','nodeType':sys.argv[1],'content':sys.argv[2],'originalLang':'ru'}))" "$1" "$2"
}

mkedge() {
  python3 -c "import json,sys; print(json.dumps({'fromNodeId':sys.argv[1],'toNodeId':sys.argv[2],'edgeType':sys.argv[3],'rationale':sys.argv[4] if len(sys.argv)>4 else None}))" "$1" "$2" "$3" "$4"
}

echo "=== Тема + корневой вопрос ==="
TOPIC_JSON=$(post_topic '{
  "title": "Дозволенность Мавлида ан-Наби",
  "description": "Спор о дозволенности празднования дня рождения Пророка ﷺ",
  "rootQuestion": "Дозволено ли мусульманам праздновать Мавлид ан-Наби?"
}')
TOPIC_ID=$(echo "$TOPIC_JSON" | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")
Q_ROOT=$(echo "$TOPIC_JSON" | python3 -c "import sys,json; print(json.load(sys.stdin)['rootNodeId'])")
echo "Topic: $TOPIC_ID"
echo "Q_ROOT: $Q_ROOT"

echo ""
echo "=== Узлы ==="

C_MAIN=$(post_node "$(mknode CLAIM "Мавлид является дозволенной практикой")")
echo "C_MAIN (главный тезис): $C_MAIN"

ARG_FOR_1=$(post_node "$(mknode ARGUMENT "Празднование - выражение любви к Пророку ﷺ, что является обязанностью верующего")")
echo "ARG_FOR_1: $ARG_FOR_1"
ARG_FOR_2=$(post_node "$(mknode ARGUMENT "Это \"бидʿа хасана\" (хорошее нововведение) - отдельная категория в богословии")")
echo "ARG_FOR_2: $ARG_FOR_2"
ARG_AGAINST_1=$(post_node "$(mknode ARGUMENT "Любая бидʿа в религии есть заблуждение - так сказал Пророк ﷺ")")
echo "ARG_AGAINST_1: $ARG_AGAINST_1"
ARG_AGAINST_2=$(post_node "$(mknode ARGUMENT "Сахаба и саляф не праздновали Мавлид")")
echo "ARG_AGAINST_2: $ARG_AGAINST_2"

EV_FOR_1=$(post_node "$(mknode EVIDENCE "Хадис: \"Не уверует никто из вас, пока я не стану ему любимее, чем его отец, дитя и все люди\" (Бухари, Муслим)")")
echo "EV_FOR_1: $EV_FOR_1"
EV_FOR_2=$(post_node "$(mknode EVIDENCE "Трактат имама ас-Суюти \"Хусн уль-максид фи амаль аль-маулид\" с богословским разделением бидʿа на пять видов")")
echo "EV_FOR_2: $EV_FOR_2"
EV_AGAINST_1=$(post_node "$(mknode EVIDENCE "Хадис: \"Каждое нововведение - бидʿа, и каждая бидʿа - заблуждение\" (Муслим)")")
echo "EV_AGAINST_1: $EV_AGAINST_1"

Q_QUALIFY=$(post_node "$(mknode QUESTION "Не приводит ли празднование к харамным практикам (смешение полов, ширк, излишество)?")")
echo "Q_QUALIFY: $Q_QUALIFY"

echo ""
echo "=== Рёбра ==="

# Ветка ЗА: ARGUMENT -SUPPORTS-> C_MAIN, EVIDENCE -SUPPORTS-> ARGUMENT
post_edge "$(mkedge $ARG_FOR_1 $C_MAIN SUPPORTS 'Любовь к Пророку обязывает чтить день его рождения')"
post_edge "$(mkedge $EV_FOR_1 $ARG_FOR_1 SUPPORTS 'Хадис прямо обязывает любить Пророка')"
post_edge "$(mkedge $ARG_FOR_2 $C_MAIN SUPPORTS 'Категория бидʿа хасана легитимизирует Мавлид')"
post_edge "$(mkedge $EV_FOR_2 $ARG_FOR_2 SUPPORTS 'Богословское разделение бидʿа даёт основание')"

# Ветка ПРОТИВ: ARGUMENT -REFUTES-> C_MAIN, EVIDENCE -SUPPORTS-> ARGUMENT
post_edge "$(mkedge $ARG_AGAINST_1 $C_MAIN REFUTES 'Прямое толкование запрета на бидʿа')"
post_edge "$(mkedge $EV_AGAINST_1 $ARG_AGAINST_1 SUPPORTS 'Хадис в Сахих Муслим')"
post_edge "$(mkedge $ARG_AGAINST_2 $C_MAIN REFUTES 'Лучшее поколение - саляф - служит образцом')"

# Мета-опровержение: трактат ас-Суюти аннулирует общий запрет "любая бидʿа = заблуждение"
post_edge "$(mkedge $EV_FOR_2 $ARG_AGAINST_1 INVALIDATES 'Богословское разделение бидʿа аннулирует обобщение')"

# Уточнение: вопрос о харамных элементах сужает применимость C_MAIN
post_edge "$(mkedge $Q_QUALIFY $C_MAIN QUALIFIES 'Дозволено только при отсутствии харама')"

# Главный тезис отвечает на корневой вопрос
post_edge "$(mkedge $C_MAIN $Q_ROOT RESPONDS_TO 'Тезис как ответ на вопрос темы')"

echo ""
echo "=== Готово ==="
echo "Topic ID: $TOPIC_ID"
echo "Открой: http://localhost:5173/topics/$TOPIC_ID"
echo ""
echo "Состав: 10 узлов (1 root QUESTION, 1 уточняющий QUESTION, 1 главный CLAIM,"
echo "       4 ARGUMENT, 3 EVIDENCE)"
echo "       10 рёбер: 5 SUPPORTS, 2 REFUTES, 1 INVALIDATES, 1 QUALIFIES, 1 RESPONDS_TO"
