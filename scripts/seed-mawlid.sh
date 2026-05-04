#!/bin/bash
set -e
USER="14561248-0bfd-4a62-8395-d40a6972182a"
H_CT="Content-Type: application/json"
H_USR="X-User-Id: $USER"

extract_id() { python3 -c "import sys,json; print(json.load(sys.stdin)['id'])"; }

post_topic() {
  curl -sS -X POST http://localhost:9090/api/v1/topics \
    -H "$H_CT" -H "$H_USR" -d "$1"
}

post_node() {
  curl -sS -X POST http://localhost:9090/api/v1/nodes \
    -H "$H_CT" -H "$H_USR" -d "$1" | extract_id
}

post_edge() {
  curl -sS -o /dev/null -w "%{http_code} " -X POST http://localhost:9090/api/v1/edges \
    -H "$H_CT" -H "$H_USR" -d "$1"
}

echo "=== Создание темы ==="
TOPIC_JSON=$(post_topic '{
  "title": "Дозволенность Мавлида ан-Наби",
  "description": "Спор о дозволенности празднования дня рождения Пророка ﷺ",
  "rootQuestion": "Дозволено ли мусульманам праздновать Мавлид ан-Наби?"
}')
TOPIC_ID=$(echo "$TOPIC_JSON" | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")
Q_ROOT=$(echo "$TOPIC_JSON" | python3 -c "import sys,json; print(json.load(sys.stdin)['rootNodeId'])")
echo "Topic: $TOPIC_ID"
echo "Q_ROOT (корневой вопрос): $Q_ROOT"

echo ""
echo "=== Создание узлов ==="

mkclaim() {
  post_node "$(python3 -c "import json,sys; print(json.dumps({'topicId':'$TOPIC_ID','nodeType':'CLAIM','content':sys.argv[1],'weight':int(sys.argv[2])}))" "$1" "$2")"
}
mkarg() {
  post_node "$(python3 -c "import json,sys; print(json.dumps({'topicId':'$TOPIC_ID','nodeType':'ARGUMENT','content':sys.argv[1],'weight':int(sys.argv[2])}))" "$1" "$2")"
}
mkev() {
  post_node "$(python3 -c "import json,sys; print(json.dumps({'topicId':'$TOPIC_ID','nodeType':'EVIDENCE','content':sys.argv[1],'weight':int(sys.argv[2])}))" "$1" "$2")"
}
mkq() {
  post_node "$(python3 -c "import json,sys; print(json.dumps({'topicId':'$TOPIC_ID','nodeType':'QUESTION','content':sys.argv[1],'weight':int(sys.argv[2])}))" "$1" "$2")"
}

C_ALLOW=$(mkclaim "Мавлид является дозволенной практикой" 7)
echo "C_ALLOW: $C_ALLOW"
C_FORBID=$(mkclaim "Мавлид является запрещённой бидʿа" 6)
echo "C_FORBID: $C_FORBID"
C_FINAL=$(mkclaim "Мавлид дозволен при отсутствии харамных элементов (смешения полов, излишеств, ширка)" 9)
echo "C_FINAL: $C_FINAL"

ARG_A1=$(mkarg "Празднование - выражение любви к Пророку ﷺ, что является обязанностью верующего" 8)
echo "ARG_A1: $ARG_A1"
ARG_A2=$(mkarg "Это \"бидʿа хасана\" (хорошее нововведение), как разделили учёные" 7)
echo "ARG_A2: $ARG_A2"
ARG_B1=$(mkarg "Любая бидʿа в религии есть заблуждение - так сказал Пророк ﷺ" 8)
echo "ARG_B1: $ARG_B1"
ARG_B2=$(mkarg "Сахаба и первые три поколения (саляф) не праздновали Мавлид" 7)
echo "ARG_B2: $ARG_B2"

EV_A1=$(mkev "Хадис: \"Не уверует никто из вас, пока я не стану любимее ему, чем его отец, дитя и все люди\" (Бухари, Муслим)" 9)
echo "EV_A1: $EV_A1"
EV_A2=$(mkev "Трактат имама ас-Суюти \"Хусн уль-максид фи амаль аль-маулид\" с богословским разделением бидʿа на пять видов" 8)
echo "EV_A2: $EV_A2"
EV_B1=$(mkev "Хадис: \"Каждое нововведение - бидʿа, и каждая бидʿа - заблуждение\" (Муслим)" 9)
echo "EV_B1: $EV_B1"

Q_QUALIFY=$(mkq "Не приводит ли празднование к харамным практикам (смешение полов, ширк, излишество)?" 6)
echo "Q_QUALIFY: $Q_QUALIFY"

echo ""
echo "=== Создание связей ==="

mkedge() {
  post_edge "$(python3 -c "import json,sys; print(json.dumps({'fromNodeId':sys.argv[1],'toNodeId':sys.argv[2],'edgeType':sys.argv[3],'rationale':sys.argv[4] if len(sys.argv)>4 else None}))" "$1" "$2" "$3" "$4")"
}

# CLAIMs за Мавлид
mkedge "$ARG_A1" "$C_ALLOW" "SUPPORTS" "Любовь к Пророку обязывает чтить день его рождения"
mkedge "$EV_A1" "$ARG_A1" "SUPPORTS" "Хадис прямо обязывает любить Пророка"
mkedge "$ARG_A2" "$C_ALLOW" "SUPPORTS" "Категория бидʿа хасана легитимизирует Мавлид"
mkedge "$EV_A2" "$ARG_A2" "SUPPORTS" "Богословское разделение даёт основание"

# CLAIMs против Мавлида
mkedge "$ARG_B1" "$C_FORBID" "SUPPORTS" "Прямое толкование хадиса о бидʿа"
mkedge "$EV_B1" "$ARG_B1" "SUPPORTS" "Хадис в Сахих Муслим"
mkedge "$ARG_B2" "$C_FORBID" "SUPPORTS" "Лучшее поколение - саляф - служит образцом"

# Мета-опровержение
mkedge "$EV_A2" "$ARG_B1" "INVALIDATES" "Трактат ас-Суюти аннулирует обобщение \"любая бидʿа = заблуждение\""

# Финальный вывод
mkedge "$C_ALLOW" "$C_FINAL" "SUPPORTS" "Сторона разрешения поддерживает финальный вывод"
mkedge "$C_FORBID" "$C_FINAL" "REFUTES" "Сторона запрета противоречит финальному выводу"
mkedge "$Q_QUALIFY" "$C_FINAL" "QUALIFIES" "Уточняющий вопрос задаёт границу: только при отсутствии харама"
mkedge "$C_FINAL" "$Q_ROOT" "RESPONDS_TO" "Финальный вывод отвечает на корневой вопрос"

echo ""
echo "=== Готово ==="
echo "Topic ID: $TOPIC_ID"
echo "Открой: http://localhost:5173/topics/$TOPIC_ID"
