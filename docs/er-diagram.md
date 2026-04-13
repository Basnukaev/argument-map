# ER-диаграмма

```mermaid
erDiagram
    TOPIC ||--o{ NODE : contains
    TOPIC }o--|| USER : created_by
    TOPIC {
        uuid id PK
        string title
        text description
        uuid root_node_id FK
        uuid created_by FK
        timestamp created_at
    }

    NODE ||--o{ EDGE : from_node
    NODE ||--o{ EDGE : to_node
    NODE ||--o{ NODE_SOURCE : cites
    NODE ||--o{ NODE_AUTHORITY : attributed_to
    NODE ||--o{ REVISION : has_history
    NODE }o--|| USER : created_by
    NODE {
        uuid id PK
        uuid topic_id FK
        string node_type "QUESTION|CLAIM|ARGUMENT|EVIDENCE"
        text content
        string status "STANDING|DISPUTED|REFUTED|UNVERIFIED"
        int weight "субъективная сила 1-10"
        uuid created_by FK
        timestamp created_at
        timestamp updated_at
    }

    EDGE {
        uuid id PK
        uuid from_node_id FK
        uuid to_node_id FK
        string edge_type "SUPPORTS|REFUTES|QUALIFIES|INVALIDATES|RESPONDS_TO"
        text rationale "опционально: почему эта связь"
        uuid created_by FK
        timestamp created_at
    }

    SOURCE ||--o{ NODE_SOURCE : referenced_by
    SOURCE {
        uuid id PK
        string source_type "QURAN|HADITH|BOOK|ARTICLE|URL"
        string title
        text citation "сура:аят / сборник №хадиса / автор, стр."
        string reliability "для хадисов: sahih|hasan|daif"
        jsonb metadata "тип-специфичные поля"
    }

    NODE_SOURCE {
        uuid node_id FK
        uuid source_id FK
        text quote "точная цитата"
        text context "комментарий"
    }

    AUTHORITY ||--o{ NODE_AUTHORITY : attributed
    AUTHORITY {
        uuid id PK
        string name
        text bio
        string era "эпоха"
        string madhab "для исламского контекста"
        jsonb metadata
    }

    NODE_AUTHORITY {
        uuid node_id FK
        uuid authority_id FK
        string stance "HOLDS|OPPOSES|NEUTRAL"
    }

    REVISION {
        uuid id PK
        uuid node_id FK
        text content_before
        text content_after
        uuid changed_by FK
        timestamp changed_at
    }

    USER {
        uuid id PK
        string username
        string email
    }
```

## Пояснения к связям

- **TOPIC → NODE** (1:N): тема содержит много узлов, но корневой — только один
  (ссылка `root_node_id` в TOPIC).
- **NODE → EDGE** (два 1:N отношения): узел может быть источником и/или
  приёмником многих рёбер.
- **NODE ↔ SOURCE** (M:N через NODE_SOURCE): один узел может цитировать
  несколько источников, один источник — использоваться во многих узлах.
- **NODE ↔ AUTHORITY** (M:N через NODE_AUTHORITY): аналогично, с полем
  `stance` — позиция учёного относительно узла.
- **NODE → REVISION** (1:N): история изменений содержимого узла.
