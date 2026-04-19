# Стратегия тестирования

## Типы тестов

### Unit-тесты (`*Test.java`)
- Тестируют один класс в изоляции
- Зависимости мокаются через Mockito
- Быстрые, без внешних ресурсов
- Запускаются через `./mvnw test`
- Покрываем: сервисы, мапперы, утилитные классы

### Интеграционные тесты (`*IT.java`)
- Поднимают Spring Context + реальный Postgres через Testcontainers
- Используют `@Import(TestcontainersConfiguration.class)` и `@ServiceConnection`
- Запускаются через `./mvnw verify`
- Покрываем: репозитории (CRUD), контроллеры (MockMvc), Liquibase-миграции

### Smoke-тест миграций
- `ArgumentMapApplicationTests.contextLoads()` — уже есть
- Проверяет что все Liquibase-миграции проходят и контекст поднимается
- Если этот тест падает — всё остальное не имеет смысла

## Подготовка тестовых данных

### Для unit-тестов: factory-методы
Статические методы в тестовом классе или в отдельном `TestDataFactory`:

```java
public final class TestDataFactory {
    public static Node createQuestion(UUID topicId) {
        return new Node(UUID.randomUUID(), topicId, NodeType.QUESTION,
                        "Тестовый вопрос?", NodeStatus.UNVERIFIED, 5, ...);
    }
    // ...
}
```

### Для интеграционных тестов: прямая вставка через JDBC
Не использовать репозитории для подготовки данных в тесте репозиториев —
тестируешь себя через себя. Вместо этого: `jdbcTemplate.update(...)`
для вставки фикстур, потом вызов тестируемого метода.

Альтернатива: DBRider с YAML-фикстурами, если стандартный подход
станет слишком громоздким (решить на Этапе 2).

## Тестирование графовых обходов (Этап 3)

Для `StatusCalculationService` нужны fixture-графы разной сложности:

### Минимальные сценарии
1. **Одиночный узел** — статус UNVERIFIED
2. **Вопрос → Тезис (SUPPORTS)** — тезис STANDING
3. **Вопрос → Тезис, Аргумент (REFUTES) Тезис** — тезис REFUTED
4. **Тезис с SUPPORTS и REFUTES** — тезис DISPUTED

### Граничные случаи
5. **Цепочка**: A supports B, B supports C — каскад
6. **INVALIDATES**: аргумент объявляет другой нерелевантным
7. **Цикл**: A supports B, B supports A — алгоритм не должен зависнуть
8. **Orphan nodes**: узлы без связей с корнем

### Как строить fixture-граф в тесте
Builder-паттерн для читаемости:

```java
// Псевдокод — точный API определится при реализации
var graph = TestGraph.builder()
    .question("Мавлид это бид'а?")
    .claim("Нет", supports("question"))
    .argument("Учёный X сказал", supports("claim-1"))
    .argument("Но хадис Y говорит иначе", refutes("claim-1"))
    .build();
```

Решить, делать ли TestGraph на Этапе 3 — зависит от количества тестов.

## Что НЕ тестировать
- Геттеры/сеттеры и records (Java гарантирует корректность)
- Spring-конфигурацию отдельно (покрывается smoke-тестом)
- Приватные методы напрямую (тестировать через публичный API)
