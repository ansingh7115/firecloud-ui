(ns broadfcui.page.workspace.create
  (:require
   [dmohs.react :as react]
   [broadfcui.common :as common]
   [broadfcui.common.components :as comps]
   [broadfcui.common.input :as input]
   [broadfcui.common.modal :as modal]
   [broadfcui.common.style :as style]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.nav :as nav]
   [broadfcui.utils :as utils]
   ))


(react/defc CreateDialog
  {:get-initial-state
   (fn [{:keys [props]}]
     {:selected-project (first (:billing-projects props))
      :protected-option :not-loaded})
   :render
   (fn [{:keys [props state refs this]}]
     [comps/OKCancelForm
      {:header "Create New Workspace"
       :ok-button {:text "Create Workspace" :onClick #(react/call :create-workspace this)}
       :get-first-element-dom-node #(@refs "project")
       :content
       (react/create-element
         [:div {:style {:marginBottom -20}}
          (when (:creating-wf @state)
            [comps/Blocker {:banner "Creating Workspace..."}])
          (style/create-form-label "Billing Project")
          (style/create-select
            {:ref "project" :value (:selected-project @state)
             :onChange #(swap! state assoc :selected-project (-> % .-target .-value))}
            (:billing-projects props))
          (style/create-form-label "Name")
          [input/TextField {:ref "wsName" :style {:width "100%"}
                            :predicates [(input/nonempty "Workspace name")
                                         (input/alphanumeric_- "Workspace name")]}]
          (style/create-textfield-hint "Only letters, numbers, underscores, and dashes allowed")
          (style/create-form-label "Description (optional)")
          (style/create-text-area {:style {:width "100%"} :rows 5 :ref "wsDescription"})
          [:div {:style {:display "flex"}}
           (style/create-form-label "Authorization Domain")
           (common/render-info-box
             {:text [:div {} [:strong {} "Note:"] [:br] "Once this workspace is associated with an Authorization Domain,
             a user can access the data only if they are a member of the Domain and have been granted read or write
             permission on the workspace. If a user with access to the workspace clones it, any Domain associations will
             be retained by the new copy. If a user tries to share the clone with a person who is not in the Domain, the
             data remains protected. " [:a {:href "#status" :target "_blank" :style {:textDecoration "none"}} "Read more about Authorization Domains."]]})]
          (style/create-select
            {:ref "authdomain"
             :onChange #(swap! state assoc :selected-authdomain (-> % .-target .-value))}
            (:groups @state))
          [comps/ErrorViewer {:error (:server-error @state)}]
          (style/create-validation-error-message (:validation-errors @state))])}])
   :component-did-mount
   (fn [{:keys [state]}]
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/groups-list)
        :headers utils/content-type=json
        :on-done (fn [{:keys [success? get-parsed-response]}]
                   (swap! state assoc :groups (conj (map (fn[g] (get-in g [:managedGroupRef :usersGroupName]))
                                                   (mapv utils/keywordize-keys (get-parsed-response false))) "Anyone who is given permission")))}))
   :create-workspace
   (fn [{:keys [props state refs]}]
     (swap! state dissoc :server-error :validation-errors)
     (if-let [fails (input/validate refs "wsName")]
       (swap! state assoc :validation-errors fails)
       (let [project (nth (:billing-projects props) (int (:selected-project @state)))
             name (input/get-text refs "wsName")
             desc (common/get-text refs "wsDescription")
             attributes (if (clojure.string/blank? desc) {} {:description desc})
             auth-domain (if (> (int (:selected-authdomain @state)) 0) {:authorizationDomain {:usersGroupName (nth (:groups @state) (int (:selected-authdomain @state)))}} nil)]
         (swap! state assoc :creating-wf true)
         (endpoints/call-ajax-orch
          {:endpoint (endpoints/create-workspace project name)
           :payload (conj {:namespace project :name name :attributes attributes} auth-domain)
           :headers utils/content-type=json
           :on-done (fn [{:keys [success? get-parsed-response]}]
                      (swap! state dissoc :creating-wf)
                      (if success?
                        (do (modal/pop-modal)
                          (nav/go-to-path :workspace-summary {:namespace project :name name}))
                        (swap! state assoc :server-error (get-parsed-response false))))}))))})


(react/defc Button
  {:render
   (fn [{:keys [props]}]
     [:div {:style {:display "inline"}}
      [comps/Button
       {:text (case (:disabled-reason props)
                :not-loaded [comps/Spinner {:text "Getting billing info..." :style {:margin 0}}]
                "Create New Workspace...")
        :icon :add-new
        :disabled? (case (:disabled-reason props)
                     nil false
                     :not-loaded "Project billing data has not yet been loaded."
                     :no-billing (comps/no-billing-projects-message)
                     "Project billing data failed to load.")
        :onClick #(modal/push-modal [CreateDialog (select-keys props [:billing-projects])])}]])})
