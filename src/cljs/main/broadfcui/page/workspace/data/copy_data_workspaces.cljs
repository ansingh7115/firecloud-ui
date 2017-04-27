(ns broadfcui.page.workspace.data.copy-data-workspaces
  (:require
    [clojure.string]
    [dmohs.react :as react]
    [clojure.set :refer [union]]
    [broadfcui.common :as common]
    [broadfcui.common.components :as comps]
    [broadfcui.common.table :as table]
    [broadfcui.common.table-utils :refer [default-toolbar-layout]]
    [broadfcui.endpoints :as endpoints]
    [broadfcui.common.style :as style]
    [broadfcui.page.workspace.data.copy-data-entities :as copy-data-entities]
    [broadfcui.utils :as utils]
    ))

(react/defc WorkspaceList
  {:render
   (fn [{:keys [props]}]
     [:div {:style {:margin "1em"}}
      [table/Table
       {:empty-message "There are no workspaces to display."
        :reorderable-columns? false
        :columns [{:header "Billing Project" :starting-width 150}
                  {:header :name :starting-width 150
                   :as-text #(get-in % [:workspace :name]) :sort-by :text
                   :content-renderer
                   (fn [ws]
                     (style/create-link {:text (get-in ws [:workspace :name])
                                         :onClick #((:onWorkspaceSelected props) ws)}))}
                  {:header "Created By" :starting-width 200}
                  (table/date-column {})
                  {:header "Access Level" :starting-width 106}
                  {:header "Authorization Domain" :starting-width 150
                   :content-renderer #(if % % "None")}]
        :toolbar (default-toolbar-layout
                  (let [num (:num-filtered props)]
                    (when (pos? num)
                      (str (:num-filtered props)
                           " workspace(s) unavailable because they contain data from other authorization domains."))))
        :data (:workspaces props)
        :->row (fn [ws]
                 (let [workspace (:workspace ws)]
                   [(:namespace workspace)
                    ws
                    (:createdBy workspace)
                    (:createdDate workspace)
                    (:accessLevel ws)
                    (get-in workspace [:authorizationDomain :usersGroupName])]))}]])})

(defn- remove-self [workspace-id workspace-list]
  (filter #(not= workspace-id {:namespace (get-in % [:workspace :namespace])
                               :name (get-in % [:workspace :name])}) workspace-list))

(defn- filter-workspaces [this-auth-domain workspace-list]
  (filter #(let [src-auth-domain (get-in % [:workspace :authorizationDomain :usersGroupName])]
             (and
              (or (nil? src-auth-domain) (= src-auth-domain this-auth-domain))
              (not= (:accessLevel %) "NO ACCESS")))
    workspace-list))

(defn- workspace->id [workspace]
  {:namespace (get-in workspace [:workspace :namespace])
   :name (get-in workspace [:workspace :name])})

(react/defc Page
  {:render
   (fn [{:keys [state props]}]
     (let [selected-workspace (:selected-workspace (first (:crumbs props)))]
       (cond
         selected-workspace
         [copy-data-entities/SelectType
          (merge (select-keys props [:workspace-id :add-crumb :on-data-imported])
                 {:selected-workspace-id (workspace->id selected-workspace)
                  :selected-workspace-bucket (get-in selected-workspace [:workspace :bucketName])
                  :crumbs (rest (:crumbs props))})]
         (:workspaces @state)
         [WorkspaceList
          {:workspaces (:workspaces @state)
           :num-filtered (:num-filtered @state)
           :onWorkspaceSelected
           (fn [ws]
             ((:add-crumb props)
              {:text (str (get-in ws [:workspace :namespace]) "/"
                          (get-in ws [:workspace :name]))
               :onClick #((:pop-to-depth props) 3)
               :selected-workspace ws}))}]
         (:error-message @state) (style/create-server-error-message (:error-message @state))
         :else [:div {:style {:textAlign "center"}}
                [comps/Spinner {:text "Loading workspaces..."}]])))
   :component-did-mount
   (fn [{:keys [state props]}]
     (endpoints/call-ajax-orch
       {:endpoint endpoints/list-workspaces
        :on-done (fn [{:keys [success? status-text get-parsed-response]}]
                   (if success?
                     (let [all-workspaces (remove-self (:workspace-id props) (get-parsed-response))
                           filtered-workspaces (filter-workspaces (:this-auth-domain props) all-workspaces)]
                       (swap! state assoc :workspaces filtered-workspaces
                         :num-filtered (- (count all-workspaces) (count filtered-workspaces))))
                     (swap! state assoc :error-message status-text)))}))})
