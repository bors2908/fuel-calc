export interface paths {
    "/api/v1/regions": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List regions
         * @description Возвращает весь небольшой справочник. Поиск по названию выполняется в UI. Сортировка по названию, затем по коду; пагинации нет.
         */
        get: operations["listRegions"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/fuel-types": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List fuel types
         * @description Возвращает все поддерживаемые виды топлива с названиями.
         */
        get: operations["listFuelTypes"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/fuel-prices/coverage": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Get archive coverage
         * @description Пакетное покрытие архива по всем запрошенным комбинациям. Не зависит от ранее загруженных справочников или выбранного диапазона.
         */
        get: operations["getFuelPriceCoverage"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/fuel-prices/overview": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Get price overview
         * @description Возвращает сводку основного региона и минимум среди выбранных по общему срезу. При отсутствии истории отвечает 200 с явными null и статусами.
         */
        get: operations["getFuelPriceOverview"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/fuel-prices/trend": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Get regional price trends
         * @description Возвращает все наблюдения интервала для всех выбранных регионов одним пакетом. Нет запросов по одному региону или точке. История и подписи достаточны для графика, таблицы и подсказок. sourceUpdates передаёт состояния потоков один раз на ответ; source.updateKey каждой цены ссылается на запись этой карты. Явные пропуски относятся к общему календарю канонического набора, а не к выдуманным дням. Пагинации и скрытого усечения нет.
         */
        get: operations["getFuelPriceTrend"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/fuel-prices/comparison": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Compare regional prices
         * @description Возвращает последний общий период всех выбранных регионов и полные строки. Если общего периода нет, показывает последние индивидуальные цены без разниц и рейтинга. Повторный запрос сортировки не требует данных предыдущего ответа.
         */
        get: operations["getFuelPriceComparison"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/fuel-prices/observations/{observationId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Get observation provenance
         * @description Вызывается при открытии подробностей конкретной цены. observationId идентифицирует неизменяемую версию наблюдения; исправление цены создаёт новый ID. Полный ответ не требует сохранённой строки графика или справочника.
         */
        get: operations["getFuelPriceObservation"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/refueling/calculate": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Calculate refueling costs
         * @description Чистый расчёт по архиву, без сохранения заказа. Полный selection позволяет вызвать напрямую. Сервер выбирает текущий общий срез, рассчитывает все регионы и возвращает сравнение и суммы в едином массиве comparison.data. expectedSnapshotId не обязателен и не заменяет selection. При изменении среза возвращается 200 с snapshotChanged=true; при отсутствии среза — 422.
         */
        post: operations["calculateRefueling"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
}
export type webhooks = Record<string, never>;
export interface components {
    schemas: {
        /** @enum {string} */
        SortOrder: "ASC" | "DESC";
        /** @description Непрозрачная ревизия опубликованного набора данных и справочников. */
        DataVersion: string;
        /** @description Детерминированный идентификатор выбранного среза; не ID серверной сессии. */
        SnapshotId: string;
        /**
         * @description Неотрицательное десятичное значение без потери исходной точности.
         * @example 72.50
         */
        DecimalString: string;
        /** @description null означает отсутствие значения; это не нулевая цена. */
        NullableDecimalString: string | null;
        /** @description Разница цен с исходной точностью; null, если сравнение невозможно. */
        NullableSignedDecimalString: string | null;
        /**
         * @description Неотрицательная сумма в рублях, ровно два знака после точки.
         * @example 43500.00
         */
        Money: string;
        /**
         * @description Разница округлённых сумм в рублях; отрицательное значение означает меньшую стоимость.
         * @example -420.00
         */
        SignedMoney: string;
        /**
         * @description Проценты с двумя знаками после точки; null при отсутствии базы.
         * @example 1.68
         */
        NullablePercent: string | null;
        /**
         * @description Положительный объём от 0.01 до 1000000.00 л включительно; максимум два знака после точки. Проверка диапазона обязательна на сервере.
         * @example 600
         */
        VolumeLiters: string;
        /** @description Метаданные ответа. serverDate — текущая календарная дата Europe/Moscow; generatedAt — RFC 3339 с часовым поясом. */
        ApiMeta: {
            requestId: string;
            /** Format: date-time */
            generatedAt: string;
            /** Format: date */
            serverDate: string;
            dataVersion: components["schemas"]["DataVersion"];
        };
        /** @description Точное строковое значение data[].code из GET /api/v1/regions. Не название региона, не короткий территориальный код и не код канала загрузки. Код сохраняется целиком, включая ведущие нули; существование проверяется по справочнику БД. */
        RegionCode: string;
        /** @enum {string} */
        FuelType: "DIESEL" | "AI_92" | "AI_95";
        /** @description Регион с отображаемым названием. */
        RegionRef: {
            code: components["schemas"]["RegionCode"];
            name: string;
        };
        /** @description Вид топлива с отображаемым названием. */
        FuelTypeRef: {
            code: components["schemas"]["FuelType"];
            name: string;
        };
        /** @description Полный список субъектов РФ по названию, затем по коду; поиск по названию выполняется в UI. */
        RegionList: {
            meta: components["schemas"]["ApiMeta"];
            data: components["schemas"]["RegionRef"][];
        };
        /** @description Поддерживаемые виды топлива в порядке DIESEL, AI_92, AI_95. */
        FuelTypeList: {
            meta: components["schemas"]["ApiMeta"];
            data: components["schemas"]["FuelTypeRef"][];
        };
        /**
         * @description Канал доставки региональной цены, независимо от publisher. Три позиции слайдера: Росстат, rustata, BenzUp.
         * @enum {string}
         */
        PriceSource: "ROSSTAT" | "RUSTATA" | "BENZUP";
        /** @description Полная цепочка с preferredSource первым. ROSSTAT: ROSSTAT,RUSTATA,BENZUP; RUSTATA: RUSTATA,ROSSTAT,BENZUP; BENZUP: BENZUP,RUSTATA,ROSSTAT. Backend выбирает цену той же ячейки; цепочка не задаётся клиентом. */
        SourceFallbackOrder: components["schemas"]["PriceSource"][];
        /** @description Фактический канал цены и его название. Полные ссылки доступны в подробностях наблюдения. */
        SourceLabel: {
            code: components["schemas"]["PriceSource"];
            name: string;
        };
        /** @description Источник или канал получения с названием; дополнительных запросов для его подписи не нужно. */
        SourceRef: {
            code: string;
            name: string;
            /** Format: uri */
            url: string | null;
        };
        /** @description Параметры среднего региональных индексов BenzUp. measurementCount — число успешных замеров; дневная цена — их среднее арифметическое. Недельная/месячная цена — среднее доступных дневных средних с равным весом дней. dayCount <= expectedDayCount; меньшее число означает неполное покрытие. История начинается с фактического сбора, прошлые дни не заполняются сегодняшними ценами. Округление до 6 знаков HALF_UP; collectedAt — последний замер, дата публикации поставщиком неизвестна. */
        RegionalPriceSample: {
            /** @example BENZUP_DAILY_MEAN_V1 */
            policyCode: string;
            /** Format: int32 */
            measurementCount: number;
            /** Format: int32 */
            dayCount: number;
            /** Format: int32 */
            expectedDayCount: number;
            /** Format: date-time */
            collectedAt: string;
        };
        /** @description Краткое происхождение цены. provider — фактический канал после fallback. updateKey ссылается на sourceUpdates того же ответа. Для официальной статистики sample отсутствует; для BenzUp методика и число замеров обязательны. Полные сведения о публикации доступны через observationId. */
        SourceSummary: {
            provider: components["schemas"]["SourceLabel"];
            updateKey: string;
            /** @enum {string} */
            aggregationMethod: "OFFICIAL_STATISTICS" | "REGIONAL_INDEX_MEAN";
            sample?: components["schemas"]["RegionalPriceSample"];
        } & ({
            /** @enum {string} */
            aggregationMethod?: "OFFICIAL_STATISTICS";
        } | {
            /** @enum {string} */
            aggregationMethod?: "REGIONAL_INDEX_MEAN";
        });
        /** @description Состояние обновления потока. READY не утверждает, что наблюдение относится к сегодняшнему дню. Пояснение UI выбирает по status. Несколько потоков одного канала могут иметь разные состояния. */
        SourceUpdate: {
            provider: components["schemas"]["PriceSource"];
            frequency: components["schemas"]["Frequency"];
            /** @enum {string} */
            status: "READY" | "DELAYED" | "UNAVAILABLE" | "UNKNOWN";
            /** Format: date-time */
            lastSuccessAt: string | null;
            /** Format: date-time */
            nextExpectedPublicationAt: string | null;
        };
        /** @description Состояния потоков запрошенных частот, по одному на ключ потока. Ключ непрозрачен: UI использует source.updateKey цены. При отсутствии цены статус выбранного канала определяется по provider и frequency записей карты. Карта может быть пустой, если потоки ещё не созданы; тогда состояние неизвестно. Отсутствие данных не скрывает сведения о недоступном источнике. */
        SourceUpdates: {
            [key: string]: components["schemas"]["SourceUpdate"];
        };
        /** @enum {string} */
        Frequency: "DAILY" | "WEEKLY" | "MONTHLY";
        /**
         * @description Региональная средняя цена: официальная статистика Росстата/rustata либо среднее замеров регионального индекса BenzUp. Методика и покрытие передаются в source.
         * @enum {string}
         */
        PriceBasis: "REGIONAL_CONSUMER_AVERAGE";
        /** @enum {string} */
        ComparisonSortBy: "PRICE" | "REGION_NAME";
        /** @description Полный входной выбор, включая обязательный preferredSource для всех блоков и расчёта. regionCodes включает baseRegionCode. Обе даты включительны; dateFrom <= dateTo <= serverDate. Показатель фиксирован: REGIONAL_CONSUMER_AVERAGE. */
        PriceSelection: {
            baseRegionCode: components["schemas"]["RegionCode"];
            regionCodes: components["schemas"]["RegionCode"][];
            fuelType: components["schemas"]["FuelType"];
            /** Format: date */
            dateFrom: string;
            /** Format: date */
            dateTo: string;
            frequency: components["schemas"]["Frequency"];
            preferredSource: components["schemas"]["PriceSource"];
        };
        /** @description Нормализованный выбор и все подписи для показа ответа без загрузки справочников. key включает preferredSource и зависит от выбора, но не от dataVersion и сортировки. */
        PriceContext: {
            key: string;
            baseRegion: components["schemas"]["RegionRef"];
            regions: components["schemas"]["RegionRef"][];
            fuelType: components["schemas"]["FuelTypeRef"];
            /** Format: date */
            dateFrom: string;
            /** Format: date */
            dateTo: string;
            frequency: components["schemas"]["Frequency"];
            preferredSource: components["schemas"]["PriceSource"];
            fallbackOrder: components["schemas"]["SourceFallbackOrder"];
            basis: components["schemas"]["PriceBasis"];
            /** @enum {string} */
            unit: "RUB/L";
        };
        /** @description Единый период региональных наблюдений всех трёх каналов. Фильтрация идёт по referenceDate. Границы недельного периода неизвестного охвата равны null. */
        PricePeriod: {
            key: string;
            frequency: components["schemas"]["Frequency"];
            /** Format: date */
            referenceDate: string;
            label: string;
            /** Format: date */
            startDate: string | null;
            /** Format: date */
            endDate: string | null;
        };
        /** @enum {string} */
        MissingPriceReason: "NO_DATA_IN_RANGE" | "NO_OBSERVATION_FOR_PERIOD";
        /** @description Доступная цена с кратким происхождением; missingReason всегда null. Состояние потока находится в sourceUpdates, последняя доступная дата — на уровне ряда, строки или сводки. */
        AvailablePriceCell: {
            priceRubPerLiter: components["schemas"]["DecimalString"];
            observationId: string;
            period: components["schemas"]["PricePeriod"];
            source: components["schemas"]["SourceSummary"];
            /** @enum {string|null} */
            missingReason: null;
        };
        /** @description Явный пропуск с обязательной причиной. Цена и ID всегда null, источник отсутствует. Период присутствует у пропущенной точки календаря. */
        MissingPriceCell: {
            /** @enum {string|null} */
            priceRubPerLiter: null;
            /** @enum {string|null} */
            observationId: null;
            period?: components["schemas"]["PricePeriod"];
            missingReason: components["schemas"]["MissingPriceReason"];
        };
        /**
         * @description Доступная цена или явный пропуск; варианты имеют разные обязательные поля.
         * @example {
         *       "priceRubPerLiter": null,
         *       "observationId": null,
         *       "missingReason": "NO_DATA_IN_RANGE"
         *     }
         */
        PriceCell: components["schemas"]["AvailablePriceCell"] | components["schemas"]["MissingPriceCell"];
        /** @description Точка календаря ряда; period присутствует также при отсутствии наблюдения. */
        PriceSeriesPoint: components["schemas"]["PriceCell"] & Record<string, unknown>;
        /** @description Границы всего архива после fallback, одна точка на период. observationCount=0 означает отсутствие данных: даты null, latestSource опущен. Наличие границ не гарантирует непрерывности истории. Состояния потоков находятся в sourceUpdates ответа. */
        Coverage: {
            region: components["schemas"]["RegionRef"];
            fuelType: components["schemas"]["FuelTypeRef"];
            frequency: components["schemas"]["Frequency"];
            /** Format: date */
            firstAvailableDate: string | null;
            /** Format: date */
            lastAvailableDate: string | null;
            /** Format: int32 */
            observationCount: number;
            latestSource?: components["schemas"]["PriceSource"];
        };
        /** @description Полное произведение запрошенных регионов, видов топлива и двух частот, включая пустые комбинации. */
        CoverageList: {
            meta: components["schemas"]["ApiMeta"];
            basis: components["schemas"]["PriceBasis"];
            preferredSource: components["schemas"]["PriceSource"];
            fallbackOrder: components["schemas"]["SourceFallbackOrder"];
            data: components["schemas"]["Coverage"][];
            sourceUpdates: components["schemas"]["SourceUpdates"];
        };
        /** @description Изменение от startPrice до latestPrice той же сводки. Конечная цена отдельно не дублируется. При одной/нуле точек разницы null; при двух и нулевой начальной цене абсолютная разница задана, percent=null. */
        PriceChange: {
            /** @enum {string} */
            status: "AVAILABLE" | "INSUFFICIENT_DATA" | "ZERO_BASE";
            startPrice: components["schemas"]["PriceCell"];
            absoluteRubPerLiter: components["schemas"]["NullableSignedDecimalString"];
            percent: components["schemas"]["NullablePercent"];
        };
        /** @enum {string} */
        ComparisonStatus: "AVAILABLE" | "SINGLE_REGION" | "NO_COMMON_PERIOD" | "NO_DATA";
        /** @description Данные карточки минимальной цены за общий срез. lowestDifferenceRubPerLiter = минимум минус цена основного региона. Для SINGLE_REGION нет минимума или разницы; UI предлагает добавить регионы. */
        ComparisonSummary: {
            status: components["schemas"]["ComparisonStatus"];
            snapshotId: string | null;
            period?: components["schemas"]["PricePeriod"];
            basePriceRubPerLiter: components["schemas"]["NullableDecimalString"];
            lowestPriceRubPerLiter: components["schemas"]["NullableDecimalString"];
            lowestRegions: components["schemas"]["RegionRef"][];
            lowestDifferenceRubPerLiter: components["schemas"]["NullableSignedDecimalString"];
            hasNewerObservations: boolean;
        };
        /** @description Самодостаточная сводка; latestPrice и change относятся к основному региону, comparison — к общему периоду выбранных регионов. */
        PriceOverview: {
            meta: components["schemas"]["ApiMeta"];
            context: components["schemas"]["PriceContext"];
            latestPrice: components["schemas"]["PriceCell"];
            /** Format: date */
            latestAvailableDate: string | null;
            change: components["schemas"]["PriceChange"];
            comparison: components["schemas"]["ComparisonSummary"];
            sourceUpdates: components["schemas"]["SourceUpdates"];
        };
        /** @description Все точки выбранного интервала по общему календарю периодов. Границы available относятся ко всему архиву этого ряда. */
        RegionPriceSeries: {
            region: components["schemas"]["RegionRef"];
            /** @enum {string} */
            status: "AVAILABLE" | "PARTIAL" | "NO_DATA";
            /** Format: date */
            firstAvailableDate: string | null;
            /** Format: date */
            lastAvailableDate: string | null;
            points: components["schemas"]["PriceSeriesPoint"][];
        };
        /** @description Один ответ для графика и таблицы всех выбранных регионов. Регионы без истории сохраняются; точки упорядочены по referenceDate, затем по ключу периода. */
        PriceTrend: {
            meta: components["schemas"]["ApiMeta"];
            context: components["schemas"]["PriceContext"];
            data: components["schemas"]["RegionPriceSeries"][];
            sourceUpdates: components["schemas"]["SourceUpdates"];
        };
        /** @description При сопоставимости price относится к общему периоду; иначе к последнему индивидуальному наблюдению внутри диапазона, разница null и обе метки false. У основного региона разница null. */
        ComparisonRow: {
            region: components["schemas"]["RegionRef"];
            isBase: boolean;
            price: components["schemas"]["PriceCell"];
            /** Format: date */
            latestAvailableDate: string | null;
            differenceRubPerLiter: components["schemas"]["NullableSignedDecimalString"];
            isLowest: boolean;
            isHighest: boolean;
        };
        /** @description AVAILABLE: последний общий период всех регионов. SINGLE_REGION: последняя цена единственного региона; без меток минимума/максимума. При NO_COMMON_PERIOD/NO_DATA snapshotId=null, period опущен, allPricesEqual=null; сортировка PRICE заменяется REGION_NAME ASC. Если все сопоставимые цены равны, allPricesEqual=true и метки минимума/максимума false у всех. */
        ComparisonInfo: {
            status: components["schemas"]["ComparisonStatus"];
            snapshotId: string | null;
            period?: components["schemas"]["PricePeriod"];
            sortBy: components["schemas"]["ComparisonSortBy"];
            sortOrder: components["schemas"]["SortOrder"];
            allPricesEqual: boolean | null;
            hasNewerObservations: boolean;
        };
        /** @description Сведения о срезе и строки регионального сравнения. Объяснение и доступные действия UI выбирает по status. */
        Comparison: components["schemas"]["ComparisonInfo"] & {
            data: components["schemas"]["ComparisonRow"][];
        };
        /** @description Самодостаточное сравнение с ценами, датами и названиями; исходные значения не нужно собирать из других ответов. */
        PriceComparison: {
            meta: components["schemas"]["ApiMeta"];
            context: components["schemas"]["PriceContext"];
            comparison: components["schemas"]["Comparison"];
            sourceUpdates: components["schemas"]["SourceUpdates"];
        };
        /** @description Полная панель происхождения одного неизменяемого наблюдения. price содержит цену, period, краткий source и единственный observationId. Полное происхождение находится рядом с price. publicationDate сохраняет известную дату без выдуманного времени; publishedAt задан только при известном времени публикации. */
        PriceObservation: {
            region: components["schemas"]["RegionRef"];
            fuelType: components["schemas"]["FuelTypeRef"];
            basis: components["schemas"]["PriceBasis"];
            /** @enum {string} */
            unit: "RUB/L";
            price: components["schemas"]["AvailablePriceCell"];
            /** Format: date */
            latestAvailableDate: string | null;
            publisher: components["schemas"]["SourceRef"];
            /** Format: uri */
            providerUrl: string | null;
            datasetId: string;
            publicationId: string | null;
            /** Format: date */
            publicationDate: string | null;
            /** Format: date-time */
            publishedAt: string | null;
            /** Format: date-time */
            loadedAt: string;
            /** Format: uri */
            publicationUrl: string | null;
            attribution: string;
            /** Format: uri */
            methodologyUrl: string | null;
            note: string | null;
        };
        /** @description Детали наблюдения не требуют ранее загруженного контекста страницы. */
        PriceObservationResponse: {
            meta: components["schemas"]["ApiMeta"];
            observation: components["schemas"]["PriceObservation"];
            sourceUpdates: components["schemas"]["SourceUpdates"];
        };
        /**
         * @description Самодостаточный запрос: сервер заново определяет актуальный сопоставимый срез по selection. В примере замените REGION_FROM_CATALOG на фактический code из GET /api/v1/regions в обоих полях. volumeLiters — JSON-строка с точкой. expectedSnapshotId необязателен и используется только для уведомления об изменении; без ID из comparison.snapshotId поле нужно опустить, а не передавать null или пустую строку.
         * @example {
         *       "selection": {
         *         "baseRegionCode": "REGION_FROM_CATALOG",
         *         "regionCodes": [
         *           "REGION_FROM_CATALOG"
         *         ],
         *         "fuelType": "DIESEL",
         *         "dateFrom": "2026-07-27",
         *         "dateTo": "2026-10-25",
         *         "frequency": "WEEKLY",
         *         "preferredSource": "ROSSTAT"
         *       },
         *       "volumeLiters": "600"
         *     }
         */
        RefuelingCalculateRequest: {
            selection: components["schemas"]["PriceSelection"];
            volumeLiters: components["schemas"]["VolumeLiters"];
            expectedSnapshotId?: components["schemas"]["SnapshotId"];
        };
        /** @description Единая строка сравнения и стоимости. totalRub=HALF_UP(volume × точная цена, 2); differenceRub=округлённый итог минус округлённый итог основного региона. Для базового differenceRub=0.00, differenceRubPerLiter=null и relation=BASE. Другие метки и разница цены имеют тот же смысл, что в ComparisonRow. */
        RefuelingRow: {
            region: components["schemas"]["RegionRef"];
            price: components["schemas"]["AvailablePriceCell"];
            /** Format: date */
            latestAvailableDate: string | null;
            differenceRubPerLiter: components["schemas"]["NullableSignedDecimalString"];
            isLowest: boolean;
            isHighest: boolean;
            totalRub: components["schemas"]["Money"];
            differenceRub: components["schemas"]["SignedMoney"];
            /** @enum {string} */
            relation: "BASE" | "LOWER" | "HIGHER" | "EQUAL";
        };
        /** @description Тот же срез и единый массив строк сравнения с денежными результатами. Для основного региона relation=BASE заменяет isBase. Строки содержат все цены, разницы и метки для синхронизации таблицы сравнения с расчётом. */
        RefuelingComparison: components["schemas"]["ComparisonInfo"] & {
            data: components["schemas"]["RefuelingRow"][];
        };
        /** @description Расчёт и полное сравнение из одного согласованного чтения. snapshotChanged=null без expectedSnapshotId, иначе результат сравнения идентификаторов. Единственный массив comparison.data содержит цены, разницы, метки и суммы; отдельного data нет. Ничего не сохраняет и не требует серверной сессии. */
        RefuelingCalculation: {
            meta: components["schemas"]["ApiMeta"];
            context: components["schemas"]["PriceContext"];
            volumeLiters: components["schemas"]["VolumeLiters"];
            /** @enum {string} */
            currency: "RUB";
            baseTotalRub: components["schemas"]["Money"];
            snapshotChanged: boolean | null;
            comparison: components["schemas"]["RefuelingComparison"];
            sourceUpdates: components["schemas"]["SourceUpdates"];
        };
        /** @description Ошибка поля или смысловая причина отказа. field=null для ошибки всего запроса. */
        ErrorDetail: {
            field: string | null;
            code: string;
            message: string;
        };
        /**
         * @description Единая ошибка в стиле astoc-core. Нет трассировок стека или технических секретов.
         * @example {
         *       "type": "InvalidInput",
         *       "message": "Объём должен быть больше нуля.",
         *       "timestamp": "2026-10-26T09:00:00Z",
         *       "requestId": "request-example",
         *       "details": [
         *         {
         *           "field": "volumeLiters",
         *           "code": "OUT_OF_RANGE",
         *           "message": "Допустимо от 0.01 до 1000000.00 л."
         *         }
         *       ]
         *     }
         */
        ErrorResponse: {
            /** @enum {string} */
            type: "InvalidInput" | "ResourceNotFound" | "DataVersionChanged" | "CalculationUnavailable" | "InternalError" | "ServiceUnavailable";
            message: string;
            /** Format: date-time */
            timestamp: string;
            requestId: string;
            details: components["schemas"]["ErrorDetail"][];
            currentDataVersion?: components["schemas"]["DataVersion"];
        };
    };
    responses: {
        /** @description Некорректные поля, коды справочников, даты, объём или состав выбора. */
        InvalidInput: {
            headers: {
                "Cache-Control": components["headers"]["NoStore"];
                [name: string]: unknown;
            };
            content: {
                "application/json": components["schemas"]["ErrorResponse"];
            };
        };
        /** @description Наблюдение с таким observationId не найдено. */
        ResourceNotFound: {
            headers: {
                "Cache-Control": components["headers"]["NoStore"];
                [name: string]: unknown;
            };
            content: {
                "application/json": components["schemas"]["ErrorResponse"];
            };
        };
        /** @description dataVersion уже не соответствует опубликованной ревизии. Передана currentDataVersion. */
        DataVersionChanged: {
            headers: {
                "Cache-Control": components["headers"]["NoStore"];
                [name: string]: unknown;
            };
            content: {
                "application/json": components["schemas"]["ErrorResponse"];
            };
        };
        /** @description Нет цены единственного региона или общего среза всех выбранных регионов. */
        CalculationUnavailable: {
            headers: {
                "Cache-Control": components["headers"]["NoStore"];
                [name: string]: unknown;
            };
            content: {
                "application/json": components["schemas"]["ErrorResponse"];
            };
        };
        /** @description Не удалось обработать запрос. */
        InternalError: {
            headers: {
                "Cache-Control": components["headers"]["NoStore"];
                [name: string]: unknown;
            };
            content: {
                "application/json": components["schemas"]["ErrorResponse"];
            };
        };
        /** @description Собственный сервис или его архив недоступен; отказ внешнего источника при доступном архиве не является этой ошибкой. */
        ServiceUnavailable: {
            headers: {
                "Cache-Control": components["headers"]["NoStore"];
                [name: string]: unknown;
            };
            content: {
                "application/json": components["schemas"]["ErrorResponse"];
            };
        };
    };
    parameters: {
        /** @description Точный data[].code из GET /api/v1/regions для основного региона; входит в filterRegionCode. */
        BaseRegionCode: components["schemas"]["RegionCode"];
        /** @description От 1 до 4 точных data[].code из GET /api/v1/regions, включая основной. Один CSV-параметр с разделителем-запятой, без пробелов, кавычек и JSON-скобок. Повтор ключа, пустой список, пустые элементы и дубликаты недопустимы. */
        FilterRegionCode: components["schemas"]["RegionCode"][];
        /** @description Один вид топлива. */
        FilterFuelType: components["schemas"]["FuelType"];
        /** @description Включительная нижняя граница referenceDate в формате YYYY-MM-DD; не позже filterDateTo. */
        FilterDateFrom: string;
        /** @description Включительная верхняя граница referenceDate в формате YYYY-MM-DD, не раньше filterDateFrom и не позже meta.serverDate из ответа API (Europe/Moscow). */
        FilterDateTo: string;
        /** @description Частота итоговых наблюдений: дни, недели или месяцы. BenzUp: среднее замеров региона за день; завершённые недели и месяцы — среднее доступных дневных значений с равным весом дней. */
        Frequency: components["schemas"]["Frequency"];
        /** @description Выбранный пользователем канал. Сначала используется его цена региона/топлива/периода, затем фиксированный fallbackOrder. Сохранённые данные не меняются, внешние API не вызываются. */
        PreferredSource: components["schemas"]["PriceSource"];
        /** @description Необязательная оптимистическая проверка ревизии: передавайте meta.dataVersion предыдущего ответа. При независимом запросе параметр можно полностью опустить; пустая строка даёт 400. Если ревизия уже изменилась, 409 DataVersionChanged; старый архив по этому токену не восстанавливается. */
        DataVersion: components["schemas"]["DataVersion"];
    };
    requestBodies: never;
    headers: {
        /** @description Ответ не требует HTTP-кеша клиента. */
        NoStore: "no-store";
    };
    pathItems: never;
}
export type $defs = Record<string, never>;
export interface operations {
    listRegions: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Successful response */
            200: {
                headers: {
                    "Cache-Control": components["headers"]["NoStore"];
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["RegionList"];
                };
            };
            400: components["responses"]["InvalidInput"];
            500: components["responses"]["InternalError"];
            503: components["responses"]["ServiceUnavailable"];
        };
    };
    listFuelTypes: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Successful response */
            200: {
                headers: {
                    "Cache-Control": components["headers"]["NoStore"];
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["FuelTypeList"];
                };
            };
            500: components["responses"]["InternalError"];
            503: components["responses"]["ServiceUnavailable"];
        };
    };
    getFuelPriceCoverage: {
        parameters: {
            query: {
                /** @description От 1 до 4 точных data[].code из GET /api/v1/regions, включая основной. Один CSV-параметр с разделителем-запятой, без пробелов, кавычек и JSON-скобок. Повтор ключа, пустой список, пустые элементы и дубликаты недопустимы. */
                filterRegionCode: components["parameters"]["FilterRegionCode"];
                /** @description Выбранный пользователем канал. Сначала используется его цена региона/топлива/периода, затем фиксированный fallbackOrder. Сохранённые данные не меняются, внешние API не вызываются. */
                preferredSource: components["parameters"]["PreferredSource"];
                /** @description Необязательный CSV-список без пробелов, например DIESEL,AI_92. Без параметра — все три вида топлива; пустое значение не передавать. */
                filterFuelType?: components["schemas"]["FuelType"][];
                /** @description Необязательная оптимистическая проверка ревизии: передавайте meta.dataVersion предыдущего ответа. При независимом запросе параметр можно полностью опустить; пустая строка даёт 400. Если ревизия уже изменилась, 409 DataVersionChanged; старый архив по этому токену не восстанавливается. */
                dataVersion?: components["parameters"]["DataVersion"];
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Successful response */
            200: {
                headers: {
                    "Cache-Control": components["headers"]["NoStore"];
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["CoverageList"];
                };
            };
            400: components["responses"]["InvalidInput"];
            409: components["responses"]["DataVersionChanged"];
            500: components["responses"]["InternalError"];
            503: components["responses"]["ServiceUnavailable"];
        };
    };
    getFuelPriceOverview: {
        parameters: {
            query: {
                /** @description Точный data[].code из GET /api/v1/regions для основного региона; входит в filterRegionCode. */
                baseRegionCode: components["parameters"]["BaseRegionCode"];
                /** @description От 1 до 4 точных data[].code из GET /api/v1/regions, включая основной. Один CSV-параметр с разделителем-запятой, без пробелов, кавычек и JSON-скобок. Повтор ключа, пустой список, пустые элементы и дубликаты недопустимы. */
                filterRegionCode: components["parameters"]["FilterRegionCode"];
                /** @description Выбранный пользователем канал. Сначала используется его цена региона/топлива/периода, затем фиксированный fallbackOrder. Сохранённые данные не меняются, внешние API не вызываются. */
                preferredSource: components["parameters"]["PreferredSource"];
                /** @description Один вид топлива. */
                filterFuelType: components["parameters"]["FilterFuelType"];
                /** @description Включительная нижняя граница referenceDate в формате YYYY-MM-DD; не позже filterDateTo. */
                filterDateFrom: components["parameters"]["FilterDateFrom"];
                /** @description Включительная верхняя граница referenceDate в формате YYYY-MM-DD, не раньше filterDateFrom и не позже meta.serverDate из ответа API (Europe/Moscow). */
                filterDateTo: components["parameters"]["FilterDateTo"];
                /** @description Частота итоговых наблюдений: дни, недели или месяцы. BenzUp: среднее замеров региона за день; завершённые недели и месяцы — среднее доступных дневных значений с равным весом дней. */
                frequency: components["parameters"]["Frequency"];
                /** @description Необязательная оптимистическая проверка ревизии: передавайте meta.dataVersion предыдущего ответа. При независимом запросе параметр можно полностью опустить; пустая строка даёт 400. Если ревизия уже изменилась, 409 DataVersionChanged; старый архив по этому токену не восстанавливается. */
                dataVersion?: components["parameters"]["DataVersion"];
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Successful response */
            200: {
                headers: {
                    "Cache-Control": components["headers"]["NoStore"];
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PriceOverview"];
                };
            };
            400: components["responses"]["InvalidInput"];
            409: components["responses"]["DataVersionChanged"];
            500: components["responses"]["InternalError"];
            503: components["responses"]["ServiceUnavailable"];
        };
    };
    getFuelPriceTrend: {
        parameters: {
            query: {
                /** @description Точный data[].code из GET /api/v1/regions для основного региона; входит в filterRegionCode. */
                baseRegionCode: components["parameters"]["BaseRegionCode"];
                /** @description От 1 до 4 точных data[].code из GET /api/v1/regions, включая основной. Один CSV-параметр с разделителем-запятой, без пробелов, кавычек и JSON-скобок. Повтор ключа, пустой список, пустые элементы и дубликаты недопустимы. */
                filterRegionCode: components["parameters"]["FilterRegionCode"];
                /** @description Выбранный пользователем канал. Сначала используется его цена региона/топлива/периода, затем фиксированный fallbackOrder. Сохранённые данные не меняются, внешние API не вызываются. */
                preferredSource: components["parameters"]["PreferredSource"];
                /** @description Один вид топлива. */
                filterFuelType: components["parameters"]["FilterFuelType"];
                /** @description Включительная нижняя граница referenceDate в формате YYYY-MM-DD; не позже filterDateTo. */
                filterDateFrom: components["parameters"]["FilterDateFrom"];
                /** @description Включительная верхняя граница referenceDate в формате YYYY-MM-DD, не раньше filterDateFrom и не позже meta.serverDate из ответа API (Europe/Moscow). */
                filterDateTo: components["parameters"]["FilterDateTo"];
                /** @description Частота итоговых наблюдений: дни, недели или месяцы. BenzUp: среднее замеров региона за день; завершённые недели и месяцы — среднее доступных дневных значений с равным весом дней. */
                frequency: components["parameters"]["Frequency"];
                /** @description Необязательная оптимистическая проверка ревизии: передавайте meta.dataVersion предыдущего ответа. При независимом запросе параметр можно полностью опустить; пустая строка даёт 400. Если ревизия уже изменилась, 409 DataVersionChanged; старый архив по этому токену не восстанавливается. */
                dataVersion?: components["parameters"]["DataVersion"];
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Successful response */
            200: {
                headers: {
                    "Cache-Control": components["headers"]["NoStore"];
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PriceTrend"];
                };
            };
            400: components["responses"]["InvalidInput"];
            409: components["responses"]["DataVersionChanged"];
            500: components["responses"]["InternalError"];
            503: components["responses"]["ServiceUnavailable"];
        };
    };
    getFuelPriceComparison: {
        parameters: {
            query: {
                /** @description Точный data[].code из GET /api/v1/regions для основного региона; входит в filterRegionCode. */
                baseRegionCode: components["parameters"]["BaseRegionCode"];
                /** @description От 1 до 4 точных data[].code из GET /api/v1/regions, включая основной. Один CSV-параметр с разделителем-запятой, без пробелов, кавычек и JSON-скобок. Повтор ключа, пустой список, пустые элементы и дубликаты недопустимы. */
                filterRegionCode: components["parameters"]["FilterRegionCode"];
                /** @description Выбранный пользователем канал. Сначала используется его цена региона/топлива/периода, затем фиксированный fallbackOrder. Сохранённые данные не меняются, внешние API не вызываются. */
                preferredSource: components["parameters"]["PreferredSource"];
                /** @description Один вид топлива. */
                filterFuelType: components["parameters"]["FilterFuelType"];
                /** @description Включительная нижняя граница referenceDate в формате YYYY-MM-DD; не позже filterDateTo. */
                filterDateFrom: components["parameters"]["FilterDateFrom"];
                /** @description Включительная верхняя граница referenceDate в формате YYYY-MM-DD, не раньше filterDateFrom и не позже meta.serverDate из ответа API (Europe/Moscow). */
                filterDateTo: components["parameters"]["FilterDateTo"];
                /** @description Частота итоговых наблюдений: дни, недели или месяцы. BenzUp: среднее замеров региона за день; завершённые недели и месяцы — среднее доступных дневных значений с равным весом дней. */
                frequency: components["parameters"]["Frequency"];
                /** @description Необязательная оптимистическая проверка ревизии: передавайте meta.dataVersion предыдущего ответа. При независимом запросе параметр можно полностью опустить; пустая строка даёт 400. Если ревизия уже изменилась, 409 DataVersionChanged; старый архив по этому токену не восстанавливается. */
                dataVersion?: components["parameters"]["DataVersion"];
                /** @description Поле сортировки; фактически применённое поле возвращается в ответе. */
                sortBy?: "PRICE" | "REGION_NAME";
                /** @description Направление основного поля. При равенстве — название ASC, затем код ASC. */
                sortOrder?: "ASC" | "DESC";
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Successful response */
            200: {
                headers: {
                    "Cache-Control": components["headers"]["NoStore"];
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PriceComparison"];
                };
            };
            400: components["responses"]["InvalidInput"];
            409: components["responses"]["DataVersionChanged"];
            500: components["responses"]["InternalError"];
            503: components["responses"]["ServiceUnavailable"];
        };
    };
    getFuelPriceObservation: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description Значение observationId доступной цены из overview, trend или comparison. Непрозрачный ID неизменяемого наблюдения; сгенерированное клиентом значение string не является существующим ID и даст 404. */
                observationId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Successful response */
            200: {
                headers: {
                    "Cache-Control": components["headers"]["NoStore"];
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PriceObservationResponse"];
                };
            };
            400: components["responses"]["InvalidInput"];
            404: components["responses"]["ResourceNotFound"];
            500: components["responses"]["InternalError"];
            503: components["responses"]["ServiceUnavailable"];
        };
    };
    calculateRefueling: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** @description Полный выбор и объём для расчёта. */
        requestBody: {
            content: {
                "application/json": components["schemas"]["RefuelingCalculateRequest"];
            };
        };
        responses: {
            /** @description Successful response */
            200: {
                headers: {
                    "Cache-Control": components["headers"]["NoStore"];
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["RefuelingCalculation"];
                };
            };
            400: components["responses"]["InvalidInput"];
            422: components["responses"]["CalculationUnavailable"];
            500: components["responses"]["InternalError"];
            503: components["responses"]["ServiceUnavailable"];
        };
    };
}
