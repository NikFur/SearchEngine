Site Search Engine

Site Search Engine — это поисковый инструмент для сайтов, который осуществляет индексацию веб-страниц, извлекает леммы из текста страниц и позволяет выполнять поиск по проиндексированным данным. В процессе индексирования заполняется БД MySQL данными об индексируемых сайтах и страниц этих сайтов, содержащихся леммах, а так же релевантностью найденных лемм, что позволяет искать страницы в независимости от формы искомого слова. 

Технологии

    Java 21 – основная версия языка для серверной части.
    Spring Boot 3.1.0 – платформа для создания автономного приложения с минимальными настройками.
    Spring Data JPA / Hibernate ORM – объектно-реляционное отображение для работы с базой данных.
    MySQL – реляционная база данных для хранения информации о сайтах, страницах, леммах и индексах.
    JSoup – библиотека для парсинга и обработки HTML-кода.
    Lombok - библиотека для создания конструкторов.
    Apache Lucene Morphology – используется для лемматизации текстов (приведение слов к канонической форме).
    Maven – система сборки и управления зависимостями.

Локальный запуск проекта
1. Подготовка окружения

    Установите JDK 21.
    Убедитесь, что у вас установлена Java Development Kit (JDK) версии 21.

    Установите MySQL.
    Установите и запустите MySQL, создайте базу данных с именем search_engine.

2. Настройка конфигурации

Отредактируйте файл src/main/resources/application.yml (или application.properties) и укажите параметры подключения к базе данных:

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/search_engine
    username: root
    password: yourpassword
    driver-class-name: com.mysql.cj.jdbc.Driver
  jpa:
    show-sql: true
    hibernate:
      ddl-auto: create  # На первом запуске создаёт схему; затем можно изменить на update или validate
      dialect: org.hibernate.dialect.MySQL8Dialect
    properties:
      hibernate:
        jdbc.time_zone: UTC

logging:
  level:
    org:
      hibernate:
        SQL: DEBUG

3. Сборка и запуск

Откройте терминал и выполните следующие команды:

# Клонируем репозиторий (замените URL на ваш)
git clone https://github.com/NikFur/SearchEngine
cd your-repository

# Собираем проект с помощью Maven
mvn clean install

Запуск приложения:

    Через Maven:

mvn spring-boot:run

Или через JAR-файл:

    java -jar target/SearchEngine-3.1.0.jar

4. Проверка работы

    Главная страница:
    Откройте браузер и перейдите по адресу: http://localhost:8080/

    API индексации и поиска:
        Запуск индексации: отправьте GET или POST запрос на /api/startIndexing
        Остановка индексации: отправьте GET или POST запрос на /api/stopIndexing
        Поиск: отправьте GET запрос на /api/search с параметрами query, site, offset, limit
        Так же все эти запросы можно сделать на странице поисковика.

Дополнительная информация

    Логирование:
    Логи выводятся в консоль с использованием SLF4J/Logback.
    Индексация:
    При запуске индексации все данные в базе очищаются, и затем база заполняется заново.
    Подсветка:
    В результатах поиска формируется сниппет, в котором ключевые слова выделяются тегом <b> (или другим HTML-форматом) с помощью серверной логики.

Примечания
    
Если для выделения слов требуется учитывать разные формы (склонения) и лемматизатор возвращает только нормальную форму, возможно, потребуется расширить функционал генерации форм слова (например, с использованием дополнительного морфологического генератора).