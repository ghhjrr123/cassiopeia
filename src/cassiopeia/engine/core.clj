(ns cassiopeia.engine.core
  "Layers over Overtone which make composition more immediate"
  (:use [overtone.live])
  (:require [cassiopeia.engine.timing :as time]))

(defn pattern!
  "Fill a buffer repeating pattern if required.
     Supports integers or notes which will be converted to midi notes"
  [buf & lists]
  (buffer-write! buf (take (buffer-size buf) (cycle (map #(if (keyword? %) (note %) %) (flatten lists))))))

(defn safe-pattern!
  "Fill a buffer repeating pattern if required.
   Supports integers or notes which will be converted to midi notes.
   Only write on a beat."
  [buf beat & lists]
  (on-trigger
   (:trig-id beat)
   (fn [& _]
     (apply pattern! [buf] lists)
     (remove-event-handler ::pattern-writer)) ::pattern-writer))

(defn pattern-seq!
  "Fill a buffer repeating pattern if required. Support expressing patterns with `x` and `o`.
     For example: `oooxxoo`"
  [buf & lists]
  (let [buf-lists (map (fn [list] (if (string? list)
                                    (map #(Integer/parseInt %)
                                         (-> list
                                             (clojure.string/replace #"o" "0")
                                             (clojure.string/replace #"x" "1")
                                             (clojure.string/split #"")))
                                    list))
                       lists)]
          (pattern! buf buf-lists)))

(defn node-over-time
  "Over time change val of `field` to end"
  [node field start end rate]
  (future (loop [vol start]
    (when (>= vol end)
      (println vol)
      (Thread/sleep 200)
      (ctl node field vol)
      (recur (- vol rate))))))

(defn overtime!
  ([thing towards] (overtime! thing towards 0.1))
  ([thing towards rate]
      (letfn [(change-fn [val]  (if (< towards @thing)
                                  (if (< (- val rate) towards)
                                    towards
                                    (- val rate))
                                  (if (> (+ val rate) towards)
                                    towards
                                    (+ val rate))))]
        (future (loop []
                  (when (not= @thing towards)
                    (Thread/sleep 200)
                    (swap! thing change-fn)
                    (println @thing)
                    (recur)))))))

(def _ nil)
(defn degrees
  "Convert degrees into pitches. If degree > 7 will automatically move to the next
   octave degree."
  ([ds] (degrees ds :major :A3))
  ([ds n] (degrees ds :major n))
  ([ds scale n]
      (let [root (note n)]
        (map (fn [degree]
               (if degree
                 (+ root (degree->interval degree scale))
                 0)) ds))))


(defn randomly-trigger
  ([change-fn] (randomly-trigger change-fn 0.5 8))
  ([change-fn chance at-beat]
      (def random-counter (atom 0))
      (on-trigger (:trig-id time/beat-1th)
                  (fn [& _]
                    (swap! count inc)
                    (when (and (= 0 (mod @random-counter beat))
                               (> (rand) chance)) (change-fn)))
                  ::beat-picker)))