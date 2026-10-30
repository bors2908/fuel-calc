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
         * @description Возвращает код enum и название поддерживаемого топлива. Коды внешних справочников остаются внутри импорта.
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
         * @description Рассчитывает стоимость объёма по OFFICIAL и MARKET в одной транзакции чтения. Вход: регионы, топливо, дата и объём. Для каждого источника используется последняя общая дата не позже запрошенной. Отсутствующий источник не блокирует второй результат.
         */
        post: operations["calculateRefueling"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/fuel-prices": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Get both price sources
         * @description Сводка, история и сравнение OFFICIAL и MARKET в одном согласованном чтении. Контекст и состояния источников возвращаются один раз. Отсутствие наблюдений одного источника не скрывает другой.
         */
        get: operations["getFuelPricePage"];
        put?: never;
        post?: never;
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
        /** @description Информационная ревизия архива на момент согласованного чтения. */
        DataVersion: string;
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
        /** @enum {string} */
        ComparisonSortBy: "PRICE" | "REGION_NAME";
        /** @description Единый период региональных наблюдений всех трёх каналов. Фильтрация идёт по referenceDate. Границы недельного периода неизвестного охвата равны null. */
        PricePeriod: {
            key: string;
            frequency: components["schemas"]["Frequency"];
            /** Format: date */
            referenceDate: string;
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
            period?: components["schemas"]["PricePeriod"];
            lowestPriceRubPerLiter: components["schemas"]["NullableDecimalString"];
            lowestRegions: components["schemas"]["RegionRef"][];
            hasNewerObservations: boolean;
        };
        /** @description Самодостаточная сводка; latestPrice и change относятся к основному региону, comparison — к общему периоду выбранных регионов. */
        PriceOverview: {
            latestPrice: components["schemas"]["PriceCell"];
            /** Format: date */
            latestAvailableDate: string | null;
            change: components["schemas"]["PriceChange"];
            comparison: components["schemas"]["ComparisonSummary"];
        };
        /** @description Сохранённые наблюдения по общему календарю источника. Недельная цена действует семь календарных дней от date. Включаются недельные наблюдения, пересекающие выбранный диапазон; дневные наблюдения ограничены диапазоном строго. */
        RegionPriceSeries: {
            region: components["schemas"]["RegionRef"];
            /** @enum {string} */
            status: "AVAILABLE" | "PARTIAL" | "NO_DATA";
            points: components["schemas"]["TrendPoint"][];
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
        };
        /** @description Ответ содержит актуальные данные одного согласованного чтения. */
        ComparisonInfo: {
            status: components["schemas"]["ComparisonStatus"];
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
        /** @description Цена и фактический поставщик: ROSSTAT, RUSTATA или BENZUP. publicationDate — дата публикации, если поставщик её сообщает; loadedAt — время загрузки. */
        PriceObservation: {
            region: components["schemas"]["RegionRef"];
            fuelType: components["schemas"]["FuelTypeRef"];
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
        };
        /** @description Единая строка сравнения и стоимости. totalRub=HALF_UP(volume × точная цена, 2); differenceRub=округлённый итог минус округлённый итог основного региона. Для базового differenceRub=0.00, differenceRubPerLiter=null и relation=BASE. Другие метки и разница цены имеют тот же смысл, что в ComparisonRow. */
        RefuelingRow: {
            region: components["schemas"]["RegionRef"];
            price: components["schemas"]["AvailablePriceCell"];
            totalRub: components["schemas"]["Money"];
            differenceRub: components["schemas"]["SignedMoney"];
            /** @enum {string} */
            relation: "BASE" | "LOWER" | "HIGHER" | "EQUAL";
        };
        /** @description Тот же срез и единый массив строк сравнения с денежными результатами. Для основного региона relation=BASE заменяет isBase. Строки содержат все цены, разницы и метки для синхронизации таблицы сравнения с расчётом. */
        RefuelingComparison: components["schemas"]["ComparisonInfo"] & {
            data: components["schemas"]["RefuelingRow"][];
        };
        /** @description Независимый результат одного источника. При отсутствии сопоставимых наблюдений baseTotalRub и comparison равны null. */
        RefuelingSourceCalculation: {
            status: components["schemas"]["ComparisonStatus"];
            baseTotalRub: string | null;
            comparison: (components["schemas"]["RefuelingComparison"] | (never | null)) | null;
            source: components["schemas"]["PriceSource"];
            frequency: components["schemas"]["Frequency"];
        };
        /** @description Два расчёта из одной ревизии архива. results всегда содержит OFFICIAL первым и MARKET вторым; даты и доступность независимы. */
        RefuelingCalculation: {
            meta: components["schemas"]["ApiMeta"];
            volumeLiters: components["schemas"]["VolumeLiters"];
            /** @enum {string} */
            currency: "RUB";
            results: components["schemas"]["RefuelingSourceCalculation"][];
            sourceUpdates: components["schemas"]["SourceUpdates"];
            context: components["schemas"]["PricePageContext"];
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
            type: "InvalidInput" | "ResourceNotFound" | "InternalError" | "ServiceUnavailable";
            message: string;
            /** Format: date-time */
            timestamp: string;
            requestId: string;
            details: components["schemas"]["ErrorDetail"][];
        };
        /** @description Исходная дата сохранённого наблюдения. Для недельной цены это начало семидневного периода; дата может предшествовать началу выбранного диапазона до 6 дней. Отсутствующая цена не заменяется нулём. */
        TrendPoint: {
            /** Format: date */
            date: string;
            priceRubPerLiter: components["schemas"]["NullableDecimalString"];
            observationId: string | null;
            /** @enum {string|null} */
            missingReason: "NO_DATA_IN_RANGE" | "NO_OBSERVATION_FOR_PERIOD" | null;
        };
        /** @description Общий выбор регионов, топлива и дат для обоих источников. */
        PricePageContext: {
            baseRegion: components["schemas"]["RegionRef"];
            regions: components["schemas"]["RegionRef"][];
            fuelType: components["schemas"]["FuelTypeRef"];
            /** Format: date */
            dateFrom: string;
            /** Format: date */
            dateTo: string;
        };
        PriceSourcePage: {
            frequency: components["schemas"]["Frequency"];
            overview: components["schemas"]["PriceOverview"];
            trend: components["schemas"]["RegionPriceSeries"][];
            comparison: components["schemas"]["Comparison"];
        };
        PricePage: {
            meta: components["schemas"]["ApiMeta"];
            context: components["schemas"]["PricePageContext"];
            sources: {
                OFFICIAL: components["schemas"]["PriceSourcePage"];
                MARKET: components["schemas"]["PriceSourcePage"];
            };
            sourceUpdates: components["schemas"]["SourceUpdates"];
            comparisonOrder: components["schemas"]["PriceSource"];
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
        /** @description Включительная нижняя граница диапазона дней в формате YYYY-MM-DD; не позже filterDateTo. Недельные наблюдения включаются при пересечении их семидневного периода с диапазоном; referenceDate может быть раньше на 6 дней. */
        FilterDateFrom: string;
        /** @description Включительная верхняя граница диапазона дней в формате YYYY-MM-DD, не раньше filterDateFrom и не позже meta.serverDate из ответа API (Europe/Moscow). */
        FilterDateTo: string;
        /** @description Частота итоговых наблюдений: дни, недели или месяцы. BenzUp: среднее замеров региона за день; завершённые недели и месяцы — среднее доступных дневных значений с равным весом дней. */
        Frequency: components["schemas"]["Frequency"];
        /** @description Источник цен. OFFICIAL поддерживает WEEKLY; MARKET поддерживает DAILY. Внешние API при чтении не вызываются. */
        Source: components["schemas"]["PriceSource"];
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
    getFuelPriceObservation: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description ID доступной цены из ответа страницы или расчёта. */
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
    getFuelPricePage: {
        parameters: {
            query: {
                /** @description Точный data[].okatoCode из GET /api/v1/regions для основного региона; входит в filterRegionCode. */
                baseRegionCode: components["parameters"]["BaseRegionCode"];
                /** @description От 1 до 4 точных data[].okatoCode из GET /api/v1/regions, включая основной. Один CSV-параметр с разделителем-запятой, без пробелов, кавычек и JSON-скобок. Повтор ключа, пустой список, пустые элементы и дубликаты недопустимы. */
                filterRegionCode: components["parameters"]["FilterRegionCode"];
                /** @description Один вид топлива. */
                filterFuelType: components["parameters"]["FilterFuelType"];
                /** @description Включительная нижняя граница диапазона дней в формате YYYY-MM-DD; не позже filterDateTo. Недельные наблюдения включаются при пересечении их семидневного периода с диапазоном; referenceDate может быть раньше на 6 дней. */
                filterDateFrom: components["parameters"]["FilterDateFrom"];
                /** @description Включительная верхняя граница диапазона дней в формате YYYY-MM-DD, не раньше filterDateFrom и не позже meta.serverDate из ответа API (Europe/Moscow). */
                filterDateTo: components["parameters"]["FilterDateTo"];
                /** @description Источник, по которому упорядочиваются регионы; по умолчанию OFFICIAL. */
                sortSource?: components["schemas"]["PriceSource"];
                sortBy?: components["schemas"]["ComparisonSortBy"];
                sortOrder?: components["schemas"]["SortOrder"];
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
                    "application/json": components["schemas"]["PricePage"];
                };
            };
            400: components["responses"]["InvalidInput"];
            500: components["responses"]["InternalError"];
            503: components["responses"]["ServiceUnavailable"];
        };
    };
}
