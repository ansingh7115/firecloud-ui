(ns broadfcui.common.entity-table
  (:require
    [clojure.set :refer [union]]
    [clojure.string :refer [blank?]]
    [dmohs.react :as react]
    [broadfcui.common :as common]
    [broadfcui.common.components :as comps]
    [broadfcui.common.style :as style]
    [broadfcui.common.table :refer [Table]]
    [broadfcui.common.table-utils :refer [default-render]]
    [broadfcui.endpoints :as endpoints]
    [broadfcui.utils :as utils]
    ))



;; for attributes referring to a single other entity
;; e.g. samples referring to participants
(defn is-single-ref? [attr-value]
  (and (map? attr-value)
       (= (set (keys attr-value)) #{:entityType :entityName})))

(defn- render-list-item [item]
  (if (is-single-ref? item)
    (:entityName item)
    item))

(react/defc EntityTable
  {:update-data
   (fn [{:keys [refs after-update]} reinitialize?]
       (react/call :refresh-rows (@refs "table"))
       (when reinitialize?
             (after-update #(react/call :reinitialize (@refs "table")))))
   :refresh
   (fn [{:keys [props state this after-update]} & [entity-type reinitialize?]]
     (endpoints/call-ajax-orch
      {:endpoint (endpoints/get-entity-types (:workspace-id props))
       :on-done (fn [{:keys [success? get-parsed-response]}]
                  (if success?
                    (let [metadata (get-parsed-response)
                          entity-types (utils/sort-match (map keyword common/root-entity-types) (keys metadata))]
                      (swap! state update :server-response assoc
                             :entity-metadata metadata
                             :entity-types entity-types
                             :selected-entity-type (or (some-> entity-type keyword) (first entity-types)))
                      (after-update #(react/call :update-data this reinitialize?)))
                    (swap! state update :server-response
                           assoc :server-error (get-parsed-response false))))}))
   :get-default-props
   (fn []
     {:empty-message "There are no entities to display."
      :attribute-renderer default-render})
   :render
   (fn [{:keys [props state this]}]
     (let [{:keys [server-response]} @state
           {:keys [server-error entity-metadata entity-types selected-entity-type]} server-response]
       [:div {}
        (when (:loading-entities? @state)
          [comps/Blocker {:banner "Loading entities..."}])
        (cond
          server-error (style/create-server-error-message server-error)
          (nil? entity-metadata) [:div {:style {:textAlign "center"}} [comps/Spinner {:text "Retrieving entity types..."}]]
          :else
          (let [attributes (map keyword (get-in entity-metadata [selected-entity-type :attributeNames]))
                attr-col-width (->> attributes count (/ (if (= :narrow (:width props)) 500 1000)) int (min 400) (max 100))
                entity-column {:header (get-in entity-metadata [selected-entity-type :idName]) :starting-width 200
                               :as-text :name :sort-by :text
                               :content-renderer (or (:entity-name-renderer props) :name)}
                attr-columns (map (fn [k] {:header (name k) :starting-width attr-col-width :sort-by :text
                                           :as-text
                                           (fn [attr-value]
                                             (cond
                                               (is-single-ref? attr-value) (:entityName attr-value)
                                               (common/attribute-list? attr-value) (map render-list-item (common/attribute-values attr-value))
                                               :else (str attr-value)))
                                           :content-renderer
                                           (fn [attr-value]
                                             (cond
                                               (is-single-ref? attr-value)
                                               (if-let [renderer (:linked-entity-renderer props)]
                                                 (renderer attr-value)
                                                 (:entityName attr-value))
                                               (common/attribute-list? attr-value)
                                               (let [items (map render-list-item (common/attribute-values attr-value))]
                                                 (if (empty? items)
                                                   (case (str selected-entity-type)
                                                     ":sample_set" "0 samples"
                                                     ":pair_set" "0 pairs"
                                                     ":participant_set" "0 participants"
                                                     "0 items")
                                                   (case (str selected-entity-type)
                                                     ":sample_set" (str (count items) " samples")
                                                     ":pair_set" (str (count items) " pairs")
                                                     ":participant_set" (str (count items) " participants")
                                                     (str (count items) " items: " (clojure.string/join ", " items)))))
                                               :else ((:attribute-renderer props) attr-value)))})
                                  attributes)
                columns (vec (cons entity-column attr-columns))]
            [Table
             (merge props
                    {:key selected-entity-type
                     :ref "table"
                     :state-key (when selected-entity-type
                                  (str (common/workspace-id->string (:workspace-id props)) ":data" selected-entity-type))
                     :columns columns
                     :column-defaults (get (:column-defaults props) (some-> selected-entity-type name))
                     :always-sort? true
                     :pagination (react/call :pagination this)
                     :filter-groups (map (fn [type]
                                           {:text (name type)
                                            :count-override (get-in entity-metadata [type :count])
                                            :pred (constantly true)})
                                         entity-types)
                     :initial-filter-group-index (utils/index-of entity-types selected-entity-type)
                     :on-filter-change (fn [index]
                                         (let [type (nth entity-types index)]
                                           (swap! state update :server-response assoc :selected-entity-type type)
                                           (when-let [func (:on-filter-change props)]
                                             (func type))))
                     :->row (fn [m]
                              (->> attributes
                                   (map #(get-in m [:attributes %]))
                                   (cons m)
                                   vec))})]))]))
   :component-did-mount
   (fn [{:keys [props this]}]
     (react/call :refresh this (:initial-entity-type props)))
   :pagination
   (fn [{:keys [props state]}]
     (let [{{:keys [entity-types]} :server-response} @state]
       (fn [{:keys [current-page rows-per-page filter-text sort-column sort-order filter-group-index]} callback]
         (if (empty? entity-types)
           (callback {:group-count 0 :filtered-count 0 :rows []})
           (let [type (if (blank? filter-group-index)
                        (:initial-entity-type props)
                        (nth entity-types filter-group-index))]
             (endpoints/call-ajax-orch
              {:endpoint (endpoints/get-entities-paginated (:workspace-id props) (name type)
                                                           {"page" current-page
                                                            "pageSize" rows-per-page
                                                            "filterTerms" (js/encodeURIComponent filter-text)
                                                            "sortField" sort-column
                                                            "sortDirection" (name sort-order)})
               :on-done (fn [{:keys [success? get-parsed-response status-text status-code]}]
                          (if success?
                            (let [{:keys [results]
                                   {:keys [unfilteredCount filteredCount]} :resultMetadata} (get-parsed-response)]
                              (callback {:group-count unfilteredCount
                                         :filtered-count filteredCount
                                         :rows results}))
                            (callback {:error (str status-text " (" status-code ")")})))}))))))})
