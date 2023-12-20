(ns parrot.core
  (:require [reagent.core :as r]
            [reagent.dom :as rdom]))

;; 状態の定義
(def listening (r/atom false))
(def recognition (r/atom nil))
(def english-voices (r/atom []))
(def log-messages (r/atom []))

;; オブジェクトの生成を確認
(defn create-recognition-object []
  (cond
    (.-SpeechRecognition js/window)
    (new js/SpeechRecognition)

    (.-webkitSpeechRecognition js/window)
    (new js/webkitSpeechRecognition)

    :else
    nil))

;; 音声リストの更新
(defn update-voices []
  (reset! english-voices
          (->> (.. js/window -speechSynthesis -getVoices)
               (array-seq)
               (filter #(re-find #"en-US" (.-lang %))))))

(defn populate-voice-list []
  
  (update-voices) ; 音声リストと定期的にリストを更新
  (js/setInterval update-voices 1000))

;; 音声合成の応答
(defn respond [text]
  (let [speechSynthesis (.-speechSynthesis js/window)
        utterance (new js/SpeechSynthesisUtterance text)]
    (when-let [selectedVoiceIndex (js/parseInt (.-value (js/document.getElementById "voiceSelection")))]
      (when-let [selectedVoice (get @english-voices selectedVoiceIndex)]
        (set! (.-voice utterance) selectedVoice)))
    (set! (.-lang utterance) "en-US")
    (.speak speechSynthesis utterance)))

;; 音声認識の開始
(defn update-speech-log [transcript]
  (let [log-element (.getElementById js/document "speech-log")
        user-text (str "<p>User: " transcript "</p>")
        bot-text (str "<p>Parrot: " transcript "</p>")]
    (set! (.-innerHTML log-element)
          (str (.-innerHTML log-element) user-text bot-text))
    (respond transcript)))

(defn process-speech-result [event]
  (let [results (.-results event)
        result-length (.-length results)
        latest-result (aget results (dec result-length))
        latest-alternative (aget latest-result 0)
        transcript (.-transcript latest-alternative)]
    (update-speech-log transcript)))

(defn initiate-recognition []
  (reset! recognition (create-recognition-object))
  (when-let [recog @recognition]
    (println recog)
    (set! (.-lang recog) "en-US")
    (set! (.-continuous recog) true)

    (set! (.-onstart recog)
          (fn [] (println "Speech recognition started")))

    (set! (.-onresult recog) process-speech-result)

    (set! (.-onerror recog)
          (fn [event]
            (println "Speech recognition error:" (.-error event))))

    (set! (.-onend recog)
          (fn [] (println "Speech recognition ended")))

    (.start recog)))

;; Reagentコンポーネントの定義
(defn start-button []
  [:button {:disabled @listening
            :on-click #(do (reset! listening true)
                           (initiate-recognition))}
   "Start Conversation"])

(defn stop-button []
  [:button {:disabled (not @listening)
            :on-click #(do (reset! listening false)
                           (when-let [recog @recognition]
                             (.stop recog)))}
   "Stop Conversation"])

(defn voice-selection []
  [:select {:id "voiceSelection"}
   (for [voice @english-voices]
     ^{:key (.name voice)}
     [:option {:value (.-name voice)} (.name voice)])])

(defn log-component []
  [:div
   (for [[index msg] (map-indexed vector @log-messages)]
     ^{:key index} [:p msg])])

(defn app []
  [:div
   [start-button]
   [stop-button]
   [voice-selection]
   [log-component]])

;; アプリケーションの初期化
(defn ^:export init []
  (populate-voice-list)
  (rdom/render [app] (.getElementById js/document "app")))
