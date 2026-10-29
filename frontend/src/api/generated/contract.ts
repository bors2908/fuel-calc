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
         * @description Возвращает территории с хотя бы одним сохранённым наблюдением любого источника, топлива и даты: okatoCode и name. includeEmptyRegions=true включает также территории без данных. Сортировка по имени, затем ОКАТО. Каталог обновляется на старте и раз в неделю, неизменившаяся выдача не перезаписывается.
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
         * @description Возвращает enum топлива, название и rosstatCode из справочника товаров Витрины.
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
         * @description Один запрос возвращает два независимых расчёта: OFFICIAL/WEEKLY первым, MARKET/DAILY вторым. Для каждого источника выбирается своя последняя общая дата регионов не позднее selection.date, по всей сохранённой истории. В context обе границы dateFrom/dateTo равны запрошенной дате; фактическая дата цены находится в comparison.period. При отсутствии данных соответствующий результат имеет статус NO_DATA или NO_COMMON_PERIOD и null вместо суммы и сравнения. Ответ 200 также при отсутствии обоих рядов. expectedSnapshotIds сравниваются отдельно по источникам. Все результаты читаются из одной ревизии архива; ничего не сохраняется.
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
        /** @description Точное строковое значение data[].okatoCode из GET /api/v1/regions. Не название региона, не короткий территориальный код и не код канала загрузки. Код сохраняется целиком, включая ведущие нули; существование проверяется по справочнику БД. */
        RegionCode: string;
        /** @enum {string} */
        FuelType: "DIESEL" | "AI_92" | "AI_95";
        /** @description Территория из полного ОКАТО Витрины, включённая в официальную выборку «Россия + ФО + регионы без городов». */
        RegionRef: {
            okatoCode: components["schemas"]["RegionCode"];
            name: string;
        };
        /** @description Вид топлива и его код в справочнике товаров Витрины Росстата. */
        FuelTypeRef: {
            code: components["schemas"]["FuelType"];
            name: string;
            /** @enum {string} */
            rosstatCode: "7804" | "7802" | "7803";
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
         * @description OFFICIAL — недельные официальные цены: Витрина Росстата, при недоступности — rustata. MARKET — дневные региональные индексы BenzUp. Источники не подменяют друг друга.
         * @enum {string}
         */
        PriceSource: "OFFICIAL" | "MARKET";
        /** @description Фактический поставщик наблюдения. Для OFFICIAL — ROSSTAT или RUSTATA, для MARKET — BENZUP. */
        SourceRef: {
            code: string;
            name: string;
            /** Format: uri */
            url: string | null;
        };
        SourceUpdate: {
            source: components["schemas"]["PriceSource"];
            frequency: components["schemas"]["Frequency"];
            /** @enum {string} */
            status: "READY" | "UNAVAILABLE" | "UNKNOWN";
            /** Format: date-time */
            lastSuccessAt: string | null;
        };
        /** @description Состояние загрузки выбранной периодичности. Ключи — OFFICIAL или MARKET. Ошибка загрузки не удаляет сохранённые цены. */
        SourceUpdates: {
            [key: string]: components["schemas"]["SourceUpdate"];
        };
        /**
         * @description WEEKLY доступна для OFFICIAL, DAILY — для MARKET. Несовместимая пара source/frequency возвращает 400.
         * @enum {string}
         */
        Frequency: "DAILY" | "WEEKLY";
        /**
         * @description Региональная средняя цена: официальная статистика Росстата/rustata либо среднее замеров регионального индекса BenzUp. Методика и покрытие передаются в source.
         * @enum {string}
         */
        PriceBasis: "REGIONAL_CONSUMER_AVERAGE";
        /** @enum {string} */
        ComparisonSortBy: "PRICE" | "REGION_NAME";
        /** @description Выбор регионов, топлива, диапазона дат и источника. Обе даты включительны; dateFrom <= dateTo <= serverDate. */
        PriceSelection: {
            baseRegionCode: components["schemas"]["RegionCode"];
            regionCodes: components["schemas"]["RegionCode"][];
            fuelType: components["schemas"]["FuelType"];
            /** Format: date */
            dateFrom: string;
            /** Format: date */
            dateTo: string;
            frequency: components["schemas"]["Frequency"];
            source: components["schemas"]["PriceSource"];
        };
        /** @description Нормализованный выбор. key зависит от параметров выбора; dataVersion отражает изменения архива. */
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
            source: components["schemas"]["PriceSource"];
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
            source: components["schemas"]["PriceSource"];
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
        /** @description Цена и фактический поставщик: ROSSTAT, RUSTATA или BENZUP. publicationDate — дата публикации, если поставщик её сообщает; loadedAt — время загрузки. */
        PriceObservation: {
            region: components["schemas"]["RegionRef"];
            fuelType: components["schemas"]["FuelTypeRef"];
            basis: components["schemas"]["PriceBasis"];
            /** @enum {string} */
            unit: "RUB/L";
            price: components["schemas"]["AvailablePriceCell"];
            /** Format: date */
            latestAvailableDate: string | null;
            /** Format: date */
            publicationDate: string | null;
            /** Format: date-time */
            loadedAt: string;
            provider: components["schemas"]["SourceRef"];
        };
        /** @description Детали наблюдения не требуют ранее загруженного контекста страницы. */
        PriceObservationResponse: {
            meta: components["schemas"]["ApiMeta"];
            observation: components["schemas"]["PriceObservation"];
            sourceUpdates: components["schemas"]["SourceUpdates"];
        };
        /** @description Регионы, топливо и дата для обоих расчётов. Используется последняя общая цена не позднее date; date <= serverDate. Источник и периодичность определяются сервером. */
        RefuelingSelection: {
            baseRegionCode: components["schemas"]["RegionCode"];
            regionCodes: components["schemas"]["RegionCode"][];
            fuelType: components["schemas"]["FuelType"];
            /** Format: date */
            date: string;
        };
        /**
         * @description Общий выбор и объём для двух расчётов. Ожидаемые ID срезов необязательны; неизвестные источники, null и пустые ID не допускаются.
         * @example {
         *       "selection": {
         *         "baseRegionCode": "REGION_FROM_CATALOG",
         *         "regionCodes": [
         *           "REGION_FROM_CATALOG"
         *         ],
         *         "fuelType": "DIESEL",
         *         "date": "2026-10-25"
         *       },
         *       "volumeLiters": "600"
         *     }
         */
        RefuelingCalculateRequest: {
            selection: components["schemas"]["RefuelingSelection"];
            volumeLiters: components["schemas"]["VolumeLiters"];
            expectedSnapshotIds?: {
                OFFICIAL?: components["schemas"]["SnapshotId"];
                MARKET?: components["schemas"]["SnapshotId"];
            };
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
        /** @description Независимый расчёт одного источника. AVAILABLE/SINGLE_REGION содержит сумму и сравнение; NO_DATA/NO_COMMON_PERIOD — null. snapshotChanged=null без ожидаемого ID, иначе сравнение с текущим срезом, в том числе исчезнувшим. */
        RefuelingSourceCalculation: {
            context: components["schemas"]["PriceContext"];
            status: components["schemas"]["ComparisonStatus"];
            baseTotalRub: string | null;
            snapshotChanged: boolean | null;
            comparison: (components["schemas"]["RefuelingComparison"] | (never | null)) | null;
        };
        /** @description Два расчёта из одной ревизии архива. results всегда содержит OFFICIAL первым и MARKET вторым; даты и доступность независимы. */
        RefuelingCalculation: {
            meta: components["schemas"]["ApiMeta"];
            volumeLiters: components["schemas"]["VolumeLiters"];
            /** @enum {string} */
            currency: "RUB";
            results: components["schemas"]["RefuelingSourceCalculation"][];
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
            type: "InvalidInput" | "ResourceNotFound" | "DataVersionChanged" | "InternalError" | "ServiceUnavailable";
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
        /** @description Точный data[].okatoCode из GET /api/v1/regions для основного региона; входит в filterRegionCode. */
        BaseRegionCode: components["schemas"]["RegionCode"];
        /** @description От 1 до 4 точных data[].okatoCode из GET /api/v1/regions, включая основной. Один CSV-параметр с разделителем-запятой, без пробелов, кавычек и JSON-скобок. Повтор ключа, пустой список, пустые элементы и дубликаты недопустимы. */
        FilterRegionCode: components["schemas"]["RegionCode"][];
        /** @description Один вид топлива. */
        FilterFuelType: components["schemas"]["FuelType"];
        /** @description Включительная нижняя граница referenceDate в формате YYYY-MM-DD; не позже filterDateTo. */
        FilterDateFrom: string;
        /** @description Включительная верхняя граница referenceDate в формате YYYY-MM-DD, не раньше filterDateFrom и не позже meta.serverDate из ответа API (Europe/Moscow). */
        FilterDateTo: string;
        /** @description Частота итоговых наблюдений: дни, недели или месяцы. BenzUp: среднее замеров региона за день; завершённые недели и месяцы — среднее доступных дневных значений с равным весом дней. */
        Frequency: components["schemas"]["Frequency"];
        /** @description Источник цен. OFFICIAL поддерживает WEEKLY; MARKET поддерживает DAILY. Внешние API при чтении не вызываются. */
        Source: components["schemas"]["PriceSource"];
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
            query?: {
                /** @description Включить территории без сохранённых наблюдений. */
                includeEmptyRegions?: boolean;
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
    getFuelPriceOverview: {
        parameters: {
            query: {
                /** @description Точный data[].okatoCode из GET /api/v1/regions для основного региона; входит в filterRegionCode. */
                baseRegionCode: components["parameters"]["BaseRegionCode"];
                /** @description От 1 до 4 точных data[].okatoCode из GET /api/v1/regions, включая основной. Один CSV-параметр с разделителем-запятой, без пробелов, кавычек и JSON-скобок. Повтор ключа, пустой список, пустые элементы и дубликаты недопустимы. */
                filterRegionCode: components["parameters"]["FilterRegionCode"];
                /** @description Источник цен. OFFICIAL поддерживает WEEKLY; MARKET поддерживает DAILY. Внешние API при чтении не вызываются. */
                source: components["parameters"]["Source"];
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
                /** @description Точный data[].okatoCode из GET /api/v1/regions для основного региона; входит в filterRegionCode. */
                baseRegionCode: components["parameters"]["BaseRegionCode"];
                /** @description От 1 до 4 точных data[].okatoCode из GET /api/v1/regions, включая основной. Один CSV-параметр с разделителем-запятой, без пробелов, кавычек и JSON-скобок. Повтор ключа, пустой список, пустые элементы и дубликаты недопустимы. */
                filterRegionCode: components["parameters"]["FilterRegionCode"];
                /** @description Источник цен. OFFICIAL поддерживает WEEKLY; MARKET поддерживает DAILY. Внешние API при чтении не вызываются. */
                source: components["parameters"]["Source"];
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
                /** @description Точный data[].okatoCode из GET /api/v1/regions для основного региона; входит в filterRegionCode. */
                baseRegionCode: components["parameters"]["BaseRegionCode"];
                /** @description От 1 до 4 точных data[].okatoCode из GET /api/v1/regions, включая основной. Один CSV-параметр с разделителем-запятой, без пробелов, кавычек и JSON-скобок. Повтор ключа, пустой список, пустые элементы и дубликаты недопустимы. */
                filterRegionCode: components["parameters"]["FilterRegionCode"];
                /** @description Источник цен. OFFICIAL поддерживает WEEKLY; MARKET поддерживает DAILY. Внешние API при чтении не вызываются. */
                source: components["parameters"]["Source"];
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
            500: components["responses"]["InternalError"];
            503: components["responses"]["ServiceUnavailable"];
        };
    };
}
