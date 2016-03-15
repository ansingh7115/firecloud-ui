(ns org.broadinstitute.firecloud-ui.page.workspace.create
  (:require
   [dmohs.react :as react]
   [org.broadinstitute.firecloud-ui.common :as common]
   [org.broadinstitute.firecloud-ui.common.components :as comps]
   [org.broadinstitute.firecloud-ui.common.dialog :as dialog]
   [org.broadinstitute.firecloud-ui.common.input :as input]
   [org.broadinstitute.firecloud-ui.common.style :as style]
   [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
   [org.broadinstitute.firecloud-ui.nav :as nav]
   [org.broadinstitute.firecloud-ui.utils :as utils]
   ))


(react/defc Dialog
  {:get-initial-state
   (fn [{:keys [props]}]
     {:selected-project (first (:billing-projects props))
      :protected-option :not-loaded})
   :render
   (fn [{:keys [props state refs this]}]
     [dialog/Dialog
      {:width 500
       :dismiss-self (:dismiss props)
       :content
       (react/create-element
        [dialog/OKCancelForm
         {:header "Create New Workspace"
          :content
          (react/create-element
           [:div {:style {:marginBottom -20}}
            (when (:creating-wf @state)
              [comps/Blocker {:banner "Creating Workspace..."}])
            (style/create-form-label "Google Project")
            (style/create-select
             {:value (:selected-project @state)
              :onChange #(swap! state assoc :selected-project (-> % .-target .-value))}
             (:billing-projects props))
            (style/create-form-label "Name")
            [input/TextField {:ref "wsName" :style {:width "100%"}
                              :predicates [(input/nonempty "Workspace name")
                                           (input/alphanumeric_- "Workspace name")]}]
            (style/create-textfield-hint "Only letters, numbers, underscores, and dashes allowed")
            (style/create-form-label "Description (optional)")
            (style/create-text-area {:style {:width "100%"} :rows 5 :ref "wsDescription"})
            [:div {:style {:marginBottom "1em"}}
             [comps/Checkbox
              {:ref "protected-check"
               :label "Workspace intended to contain NIH protected data"
               :disabled? (not= (:protected-option @state) :enabled)
               :disabled-text (case (:protected-option @state)
                                :not-loaded "Account status has not finished loading."
                                :not-available "This option is not available for your account."
                                nil)}]]
            [comps/ErrorViewer {:error (:server-error @state)}]
            (style/create-validation-error-message (:validation-errors @state))])
          :dismiss-self (:dismiss props)
          :ok-button
          (react/create-element
           [comps/Button
            {:text "Create Workspace" :ref "createButton"
             :onClick #(react/call :create-workspace this)}])}])}])
   :component-did-mount
   (fn [{:keys [state]}]
     (utils/ajax-orch
      "/nih/status"
      {:on-done (fn [{:keys [success? get-parsed-response]}]
                  (if (and success? (get (get-parsed-response) "isDbgapAuthorized"))
                    (swap! state assoc :protected-option :enabled)
                    (swap! state assoc :protected-option :not-available)))}))
   :create-workspace
   (fn [{:keys [props state refs]}]
     (swap! state dissoc :server-error :validation-errors)
     (if-let [fails (input/validate refs "wsName")]
       (swap! state assoc :validation-errors fails)
       (let [project (nth (:billing-projects props) (int (:selected-project @state)))
             name (input/get-text refs "wsName")
             desc (common/get-text refs "wsDescription")
             attributes (if (clojure.string/blank? desc) {} {:description desc})
             protected? (react/call :checked? (@refs "protected-check"))]
         (swap! state assoc :creating-wf true)
         (endpoints/call-ajax-orch
          {:endpoint (endpoints/create-workspace project name)
           :payload {:namespace project :name name :attributes attributes :isProtected protected?}
           :headers {"Content-Type" "application/json"}
           :on-done (fn [{:keys [success? get-parsed-response]}]
                      (swap! state dissoc :creating-wf)
                      (if success?
                        (do ((:dismiss props))
                          (nav/navigate (:nav-context props) (str project ":" name)))
                        (swap! state assoc :server-error (get-parsed-response))))}))))})


(react/defc Button
  {:get-initial-state
   (fn []
     {:disabled-reason :not-loaded})
   :render
   (fn [{:keys [props state]}]
     (assert (:nav-context props) "Missing :nav-context prop")
     [:div {:style {:display "inline"}}
      (when (:show-dialog? @state)
        [Dialog {:dismiss #(swap! state dissoc :show-dialog?)
                 :billing-projects (:billing-projects @state)
                 :nav-context (:nav-context props)}])
      [comps/Button
       {:text "Create New Workspace..." :style :add
        :disabled? (case (:disabled-reason @state)
                     nil false
                     :not-loaded "Project billing data has not yet been loaded."
                     :no-billing (str "You must have a billing project associated with your account"
                                      " to create a new workspace.")
                     "Project billing data failed to load.")
        :onClick #(swap! state assoc :show-dialog? true)}]])
   :component-did-mount
   (fn [{:keys [state]}]
     (endpoints/call-ajax-orch
      {:endpoint (endpoints/get-billing-projects)
       :on-done (fn [{:keys [success? get-parsed-response]}]
                  (if success?
                    (let [billing-projects (get-parsed-response)]
                      (swap! state assoc :billing-projects billing-projects)
                      (if (empty? billing-projects)
                        (swap! state assoc :disabled-reason :no-billing)
                        (swap! state dissoc :disabled-reason)))
                    (swap! state assoc :disabled-reason :error)))}))})