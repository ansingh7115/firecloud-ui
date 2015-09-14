(ns org.broadinstitute.firecloud-ui.page.workspace.method-configs-tab
  (:require
    [dmohs.react :as react]
    clojure.string
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.icons :as icons]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.common.table :as table]
    [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
    [org.broadinstitute.firecloud-ui.page.workspace.method-config-importer :as importmc]
    [org.broadinstitute.firecloud-ui.page.workspace.method-config-editor :refer [MethodConfigEditor]]
    ))


(defn- render-map [m keys labels]
  [:div {}
   (map-indexed
     (fn [i k]
       [:div {}
        [:span {:style {:fontWeight 200}} (str (labels i) ": ")]
        [:span {:style {:fontweight 500}} (get m k)]])
     keys)])


(react/defc MethodConfigurationsList
  {:render
   (fn [{:keys [props state]}]
     [:div {}
      (when (:show-import-overlay? @state)
        [comps/Dialog
         {:blocker? true
          :width "80%"
          :dismiss-self #(swap! state dissoc :show-import-overlay?)
          :content (importmc/render-import-overlay (:workspace-id props)
                     #(swap! state dissoc :show-import-overlay?)
                     (:on-config-imported props) nil)}])
      [:div {:style {:float "right" :padding "0 2em 1em 0"}}
       [comps/Button {:text "Import Configuration ..."
                      :onClick #(swap! state assoc :show-import-overlay? true)}]]
      (common/clear-both)
      (let [server-response (:server-response @state)
            {:keys [configs error-message]} server-response]
        (cond
          (nil? server-response) [comps/Spinner {:text "Loading configurations..."}]
          error-message (style/create-server-error-message error-message)
          :else
          [table/Table
           {:empty-message "There are no method configurations to display."
            :columns
            [{:header "Name" :starting-width 240 :sort-by #(% "name") :filter-by #(% "name")
              :content-renderer
              (fn [row-index config]
                [:a {:href "javascript:;"
                     :style {:color (:button-blue style/colors) :textDecoration "none"}
                     :onClick #((:on-config-selected props) config)}
                 (config "name")])}
             {:header "Namespace" :starting-width 200 :sort-by :value}
             {:header "Root Entity Type" :starting-width 140 :sort-by :value}
             {:header "Method Store Method" :starting-width 300
              :filter-by #(str (% "methodNamespace") (% "methodName") (% "methodVersion"))
              :content-renderer
              (fn [row-index msm]
                (render-map msm
                  ["methodNamespace" "methodName" "methodVersion"]
                  ["Namespace" "Name" "Version"]))}
             {:header "Method Store Configuration" :starting-width 300
              :filter-by #(str (% "methodConfigNamespace") (% "methodConfigName") (% "methodConfigVersion"))
              :content-renderer
              (fn [row-index msc]
                (render-map msc
                  ["methodConfigNamespace" "methodConfigName" "methodConfigVersion"]
                  ["Namespace" "Name" "Version"]))}]
            :data (map
                    (fn [config]
                      [config
                       (config "namespace")
                       (config "rootEntityType")
                       (config "methodStoreMethod")
                       (config "methodStoreConfig")])
                    configs)}]))])
   :component-did-mount
   (fn [{:keys [this]}]
     (react/call :load-method-configs this))
   :component-did-update
   (fn [{:keys [this state]}]
     (when (nil? (:server-response @state))
       (react/call :load-method-configs this)))
   :load-method-configs
   (fn [{:keys [props state]}]
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/list-workspace-method-configs (:workspace-id props))
        :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                   (if success?
                     (swap! state assoc :server-response {:configs (vec (get-parsed-response))})
                     (swap! state assoc :server-response {:error-message status-text})))}))})


(react/defc Page
  {:render
   (fn [{:keys [props state]}]
     [:div {:style {:padding "1em 0"}}
      (if (:selected-method-config @state)
        [MethodConfigEditor {:config (:selected-method-config @state)
                             :workspace-id (:workspace-id props)
                             :on-submission-success (:on-submission-success props)
                             :on-rm (fn [] (swap! state dissoc :selected-method-config))}]
        [MethodConfigurationsList
         {:workspace-id (:workspace-id props)
          ;TODO: For both callbacks - rename config to config-id and follow the workspace-id pattern
          :on-config-selected (fn [config]
                                (swap! state assoc :selected-method-config config))
          :on-config-imported (fn [config]
                                (swap! state assoc :selected-method-config config))}])])
   :component-will-receive-props
   (fn [{:keys [state]}]
     (swap! state dissoc :selected-method-config))})


(defn render [workspace-id on-submission-success]
  [Page {:workspace-id workspace-id :on-submission-success on-submission-success}])