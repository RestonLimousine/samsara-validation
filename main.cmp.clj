{:ns main
 :require [[element.core :as el :src "https://raw.githubusercontent.com/fofx-software/element/master/core.cmp.clj"]]}

(def files (atom {}))

(defn get-scr-headers
  [[headers]]
  {:driver-code (get-key headers "DriverCode")
   :pickup-time (get-key headers "PickupDateTime")
   :shuttle-start (get-key headers "SegmentStartDateTime")
   :charter-start (get-key headers "EnRouteToPickupDateTime")
   :shuttle-end (get-key headers "SegmentEndDateTime")})

(defn sort-scr
  [scr {:keys [driver-code pickup-time]}]
  (sort-by
    (fn [row] [(nth row driver-code) (nth row pickup-time)])
    (rest scr)))

(defn sort-sms
  [sms]
  (sort-by
    (fn [{:strs [notes logStartMs]}]
      (vector
        (-> (.split notes #"\n") first .trim)
        logStartMs))
    sms))

(defn compare-files
  [{sms "upload-samsara" scr "upload-santa-cruz"}]
  (let [scr-headers (get-scr-headers scr)
        {:keys [shuttle-start charter-start shuttle-end]} scr-headers
        scr (sort-scr scr scr-headers)
        sms (sort-sms sms)]
    (reduce
      (fn [bad job]
        (let [start (nth job shuttle-start (nth job charter-start))
              end (nth job shuttle-end)]
          ))
      []
      scr)
    ))

(defn send-req
  [uri {:keys [method params]}]
  (let [method (or method "GET")
        params (or params {})
        uri (str "https://api.samsara.com/v1" uri "?access_token=" js/accessToken)
        promise (promise)]
    (.then (js/fetch uri)
      (fn [data]
        (-> (.json data)
            (.then (partial put! promise)))))
    promise))

(defn get-drivers
  [scr]
  (let [[start end]
          (map
            (fn [w]
              (.getTime (new js/Date (aget (w scr) "PickupDateTime"))))
            [first last])]
    (->promise (send-req "/fleet/drivers" {}))))

(defn get-sms-data
  [scr]
  (after-> (get-drivers scr)
           (->> (.log js/console))))

(defn compare-to-sms
  [scr]
  (let [scr (sort-by (fn [row] (aget row "PickupDateTime")) scr)]
    (after (get-sms-data scr)
      (fn [sms]
        ))))

(def file-reader
  (let [reader (new js/FileReader)]
    (.addEventListener reader "loadend"
      (fn [e]
        (let [result (new js/Uint8Array (.-result reader))
              book (.read js/XLSX result (object "type" "array"))
              first-sheet (-> book .-SheetNames (aget 0))
              sheet (-> book .-Sheets (aget first-sheet))
              rows (-> js/XLSX .-utils (.sheet_to_json sheet))]
          (compare-to-sms rows))))
    reader))

(el/append-child el/body
  (vector "div" {}
    ["h4" {} "Upload Santa Cruz Export"]
    (vector "input"
      {"type" "file"
       :on {"change"
            (fn [input e]
              (.readAsArrayBuffer file-reader
                (aget (.-files input) 0)))}})))
