(ns tupelo.java-time.epoch
  (:refer-clojure :exclude [range])
  (:use tupelo.core)
  (:require
    [clojure.walk :as walk]
    [schema.core :as s]
    [tupelo.interval :as interval]
    [tupelo.java-time :as tjt]
    [tupelo.schema :as tsk]
    [tupelo.string :as str]
    [tupelo.tagval :as tv]
    )
  (:import
    [java.time LocalDate LocalDateTime ZoneId ZonedDateTime Instant Period Year YearMonth LocalDateTime]
    [java.time.format DateTimeFormatter]
    [java.time.temporal Temporal TemporalAdjusters TemporalAccessor TemporalAmount ChronoUnit]
    [java.util Date]
    [tupelo.interval Interval]
    ))

;-----------------------------------------------------------------------------
; Remember that in java.time, there are no leap seconds!
(def SECOND->MILLIS 1000)
(def MINUTE->SECONDS 60)
(def HOUR->MINUTES 60)
(def DAY->HOURS 24)
(def YEAR->MONTHS 12)

(def HOUR->SECONDS (* HOUR->MINUTES MINUTE->SECONDS) )
(def DAY->SECONDS (* DAY->HOURS HOUR->MINUTES MINUTE->SECONDS) )
(def DAY->MILLIS (* DAY->HOURS HOUR->MINUTES MINUTE->SECONDS SECOND->MILLIS) )

;-----------------------------------------------------------------------------
; larger to smaller units => exact calculation

(s/defn seconds->millis :- s/Int
  "Converts integer seconds to milliseconds"
  [sec :- s/Int] (* sec SECOND->MILLIS))

(s/defn minutes->seconds :- s/Int
  "Converts integer minutes to seconds"
  [min :- s/Int] (* min MINUTE->SECONDS))

(s/defn hours->minutes :- s/Int
  "Converts integer hours to minutes"
  [hours :- s/Int] (* hours HOUR->MINUTES))

(s/defn hours->seconds :- s/Int
  "Converts integer hours to seconds"
  [hours :- s/Int] (* hours HOUR->SECONDS))

(s/defn days->seconds :- s/Int
  "Converts integer hours to seconds"
  [days :- s/Int] (* days DAY->SECONDS))

;-----------------------------------------------------------------------------
; smaller to larger units => truncation

(s/defn millis->seconds :- s/Int
  "Converts integer milliseconds to seconds, with truncation"
  [millis :- s/Int] (quot millis SECOND->MILLIS))

(s/defn seconds->minutes :- s/Int
  "Converts integer seconds to minutes, with truncation"
  [seconds :- s/Int] (quot seconds MINUTE->SECONDS))

(s/defn minutes->hours :- s/Int
  "Converts integer minutes to hours, with truncation"
  [minutes :- s/Int] (quot minutes HOUR->MINUTES))

(s/defn seconds->hours :- s/Int
  "Converts integer seconds to hours, with truncation"
  [seconds :- s/Int] (quot seconds HOUR->SECONDS))

(s/defn seconds->days :- s/Int
  "Converts integer seconds to hours, with truncation"
  [seconds :- s/Int] (quot seconds DAY->SECONDS))

;-----------------------------------------------------------------------------
; All "epoch time" quantities expressed as a tupelo.tagval so that one knows what "type" the integer represents
(def ENano {:enano s/Int})
(def EMilli {:emilli s/Int})
(def ESec {:esec s/Int})
(def EDay {:eday s/Int})
(def EMonth {:emonth s/Int})
(def EQtr {:eqtr s/Int})

;-----------------------------------------------------------------------------
(s/defn enano? :- s/Bool
  [arg :- s/Any] (and (tv/tagval? arg) (= :enano (tv/tag arg)) (int? (tv/val arg))))
(s/defn emilli? :- s/Bool
  [arg :- s/Any] (and (tv/tagval? arg) (= :emilli (tv/tag arg)) (int? (tv/val arg))))
(s/defn esec? :- s/Bool
  [arg :- s/Any] (and (tv/tagval? arg) (= :esec (tv/tag arg)) (int? (tv/val arg))))
(s/defn eday? :- s/Bool
  [arg :- s/Any] (and (tv/tagval? arg) (= :eday (tv/tag arg)) (int? (tv/val arg))))
(s/defn emonth? :- s/Bool
  [arg :- s/Any] (and (tv/tagval? arg) (= :emonth (tv/tag arg)) (int? (tv/val arg))))
(s/defn eqtr? :- s/Bool
  [arg :- s/Any] (and (tv/tagval? arg) (= :eqtr (tv/tag arg)) (int? (tv/val arg))))

;-----------------------------------------------------------------------------
(def ^:no-doc epoch-Year (Year/parse "1970"))
(def ^:no-doc epoch-YearMonth (YearMonth/parse "1970-01"))
(def ^:no-doc epoch-LocalDate  LocalDate/EPOCH)
(def ^:no-doc epoch-LocalDateTime (LocalDateTime/parse "1970-01-01t00:00")) ; seconds optional here
(def ^:no-doc epoch-Instant  Instant/EPOCH)

;-----------------------------------------------------------------------------
; NOTE: All "Epoch" units are ambiguous regarding timezone. Could be local or UTC.
; #todo add esec (eg Instant.getEpochSecond), eweek, emonth, equarter, quarter-of-year, year-quarter

; #todo inline?
(s/defn LocalDate->eday :- EDay ; #todo generalize & test for negative eday
  "Normalizes a LocalDate as the offset from 1970-1-1"
  [arg :- LocalDate] {:eday (.between ChronoUnit/DAYS epoch-LocalDate arg)})

(s/defn eday->LocalDate :- LocalDate
  "Given an eday, returns a LocalDate "
  [arg :- EDay] (.plusDays epoch-LocalDate (tv/val arg)))

; #todo inline?
(s/defn Instant->esec :- ESec ; #todo generalize & test for negative eday
  "Normalizes a LocalDate as the offset from 1970-1-1"
  [arg :- Instant]
  {:esec (tjt/between ChronoUnit/SECONDS epoch-Instant arg)})

;(s/defn eday->Instant :- LocalDate
;  "Given an eday, returns a LocalDate "
;  [arg :- EDay] (.plusDays epoch-LocalDate (tv/val arg)))

;(s/defn eday->monthValue :- s/Int
;  "Given an eday, returns a monthValue in [1..12]"
;  [arg :- EDay] (.getMonthValue (eday->LocalDate arg)))
;
;(s/defn LocalDateStr->eday :- EDay
;  "Parses a LocalDate string like `1999-12-31` into an integer eday (rel to epoch) like 10956"
;  [arg :- s/Str] (-> arg (LocalDate/parse) (LocalDate->eday)))
;
;(s/defn eday->LocalDateStr :- s/Str
;  "Converts an integer eday like 10956 (rel to epoch) into a LocalDate string like `1999-12-31` "
;  [arg :- EDay] (-> arg (eday->LocalDate) (str)))

(s/defn eday->year :- s/Int
  "Given an eday, returns a year like 2013"
  [arg :- EDay] (.getYear (eday->LocalDate arg)))

(s/defn ->eday :- EDay
  [arg]
  (cond
    (string? arg) (LocalDate->eday (tjt/->LocalDate (str/trim arg)))
    (int? arg) {:eday arg} ; #todo add other types
    (instance? LocalDate arg) (LocalDate->eday arg)
    (instance? Instant arg) (->eday (tjt/->LocalDate arg))
    (instance? ZonedDateTime arg) (->eday (tjt/->LocalDate arg))
    ; (instance? org.joda.time.ReadableInstant arg) (->eday (tjt/->Instant arg)) ; #todo need test
    :else (throw (ex-info "Invalid arg type" {:type (type arg) :arg arg}))))

; #todo use .toEpochSecond & .ofEpochSecond
(s/defn ->esec :- ESec           ; #todo finish
  [arg]
  (cond
    (string? arg) (->esec (tjt/truncated-to (tjt/->Instant arg) ChronoUnit/SECONDS))
    (int? arg) {:esec arg} ; #todo add other types
    (instance? Instant arg) (Instant->esec arg)
    (instance? ZonedDateTime arg) (->esec (tjt/->Instant arg))
    ; (instance? org.joda.time.ReadableInstant arg) (->esec (tjt/->Instant arg)) ; #todo need test
    :else (throw (ex-info "Invalid arg type" {:type (type arg) :arg arg}))))

; #todo: & "str->XXX" as (Instant->XXX (str->Instant XXX))
; #todo: Constructor functions
; ->enano
; ->emilli ; use .toEpochMillis & .ofEpochMillis
; ->eqtr
; ->emonth
; ->year
; #todo source: Instant, ZDT

; #todo eXXX->Instant
; #todo maybe eXXX->eYYY (sec/day, etc)

(s/defn between :- s/Int ; #todo test
  "Returns the integer difference between two epoch-vals (- ev2 ev1)"
  [epoch-val-1 :- tsk/TagVal
   epoch-val-2 :- tsk/TagVal]
  (let [tag1 (tv/tag epoch-val-1)
        tag2 (tv/tag epoch-val-2)
        int1 (tv/val epoch-val-1)
        int2 (tv/val epoch-val-2)]
    (when (not= tag1 tag2)
      (throw (ex-info "incompatible epoch values " (vals->map epoch-val-1 epoch-val-2))))
    (- int2 int1)))

; #todo need fns for add, subtract, etc ???

(comment

  (s/defn eday->quarter :- EQtr
    [eday :- EQtr]
    )

  ;#todo year-quarter => like "2013-Q1"
  (s/defn eday->eqtr :- EQtr
    [arg :- EDay]
    (let [month-value (.getMonthValue arg) ; 1..12
          month-idx   (dec month-value) ; 0..11
          quarter-idx (quot month-idx 3)
          ]
      result))

  ;#todo year-quarter => like "2013-Q1"
  (s/defn ->year-quarter :- tsk/Quarter ;#todo rename quarter-of-year
    "Given a date-ish value (e.g. LocalDate, et al), returns the quarter of the year
    as one of #{ :Q1 :Q2 :Q3 :Q4 } "
    [arg]
    (let [month-value (.getMonthValue arg) ; 1..12
          month-idx   (dec month-value) ; 0..11
          quarter-idx (quot month-idx 3)
          result      (nth year-quarters-sorted-vec quarter-idx)]
      result))

  (s/defn eday->year-quarter :- tsk/Quarter
    "Like `->year-quarter` but works for eday values"
    [eday :- s/Int] (-> eday (eday->LocalDate) (->year-quarter)))

  )

(comment
  (s/defn LocalDateStr-interval->eday-interval :- Interval ; #todo kill this?
    [itvl :- Interval]
    (with-map-vals itvl [lower upper]
      (assert (and (LocalDateStr? lower) (LocalDateStr? upper)))
      (interval/new
        (LocalDateStr->eday lower)
        (LocalDateStr->eday upper))))

  (s/defn LocalDate->trailing-interval ; #todo kill this? at least specify type (slice, antislice, closed...?)
    "Returns a LocalDate interval of span N days ending on the date supplied"
    [localdate :- LocalDate
     N :- s/Num]
    (let [ld-start (.minusDays localdate N)]
      (interval/new ld-start localdate)))
  )

