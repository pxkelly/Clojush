;; mumble.clj
;; Peter Kelly, pxkelly@hamilton.edu
;;

(ns clojush.problems.software.mumble
  (:use clojush.pushgp.pushgp
        [clojush pushstate interpreter random util globals]
        clojush.instructions.tag
        clojure.math.numeric-tower)
    (:require [clojure.string :as str]))

;; Define test cases
(defn mumble-input
  "Makes a Mumble input of length len."
  ; Code found from https://stackoverflow.com/questions/27053726/how-to-generate-random-password-with-the-fixed-length-in-clojure
  [len]
  (let [chars-between #(map char (range (int %1) (inc (int %2))))
        chars (concat (chars-between \a \z)
                      (chars-between \A \Z))
        word (take len (repeatedly #(rand-nth chars)))]
          (reduce str word)))

; Atom generators
(def mumble-atom-generators
  (concat (list
            ;;; end constants
            (fn [] (lrand-nth (map char (range 97 122)))) ;Visible character ERC
            (fn [] (mumble-input (lrand-int 10))) ;String ERC
            ;;; end ERCs
            (tag-instruction-erc [:exec :integer :boolean :string :char] 1000)
            (tagged-instruction-erc 1000)
            ;;; end tag ERCs
            'in1
            ;;; end input instructions
            )
          (registered-for-stacks [:integer :boolean :string :char :exec])))

;; A list of data domains for the problem. Each domain is a vector containing
;; a "set" of inputs and two integers representing how many cases from the set
;; should be used as training and testing cases respectively. Each "set" of
;; inputs is either a list or a function that, when called, will create a
;; random element of the set.
(def mumble-data-domains
  [[(list "a" ; single char
          "rQaTR" ; mixed case
          "abcd"  ; all lower case
          "ASDF"  ; all uppercase
          "abcdDCBApQ"  ; max length
          ) 5 0] ;; "Special" inputs covering some base cases
   [(fn [] (mumble-input (inc (lrand-int 10)))) 195 2000]
   ])

;;Can make Mumble test data like this:
;(test-and-train-data-from-domains mumble-data-domains)

; Helper function for error function
(defn mumble-test-cases
  "Takes a sequence of inputs and gives IO test cases of the form
   [input output]."
  [inputs]
  (map (fn [in]
          (vector in
            (loop [index 0 newStr ""]
              (if (= index (count (str in))) (apply str (drop-last newStr))
                  (recur (inc index) (str newStr (str/capitalize (apply str (repeat (inc index) (nth (str in) index)))) "-"))))))
       inputs))

(defn get-mumble-train-and-test
  "Returns the train and test cases."
  [data-domains]
  (map mumble-test-cases
      (test-and-train-data-from-domains data-domains)))

; Define train and test cases
(def mumble-train-and-test-cases
  (get-mumble-train-and-test mumble-data-domains))

(defn make-mumble-error-function-from-cases
  [train-cases test-cases]
  (fn the-actual-mumble-error-function
    ([individual]
      (the-actual-mumble-error-function individual :train))
    ([individual data-cases] ;; data-cases should be :train or :test
     (the-actual-mumble-error-function individual data-cases false))
    ([individual data-cases print-outputs]
      (let [behavior (atom '())
            errors (doall
                     (for [[input correct-output] (case data-cases
                                                    :train train-cases
                                                    :test test-cases
                                                    [])]
                       (let [final-state (run-push (:program individual)
                                                   (->> (make-push-state)
                                                     (push-item input :input)))
                             result (stack-ref :string 0 final-state)]
                         (when print-outputs
                           (println (format "\n| Correct output: %s\n| Program output: %s" (str correct-output) (str result))))
                         ; Record the behavior
                         (swap! behavior conj result)
                         ; Error is Levenshtein distance for string
                         (levenshtein-distance correct-output result)
                         )))]
        (if (= data-cases :train)
          (assoc individual :behaviors @behavior :errors errors)
          (assoc individual :test-errors errors))))))

(defn mumble-initial-report
  [argmap]
  (println "Train and test cases:")
  (doseq [[i case] (map vector (range) (first mumble-train-and-test-cases))]
    (println (format "Train Case: %3d | Input/Output: %s" i (str case))))
  (doseq [[i case] (map vector (range) (second mumble-train-and-test-cases))]
    (println (format "Test Case: %3d | Input/Output: %s" i (str case))))
  (println ";;******************************"))

(defn mumble-report
  "Custom generational report."
  [best population generation error-function report-simplifications]
  (let [best-test-errors (:test-errors (error-function best :test))
        best-total-test-error (apply +' best-test-errors)]
    (println ";;******************************")
    (printf ";; -*- Mumble problem report - generation %s\n" generation)(flush)
    (println "Test total error for best:" best-total-test-error)
    (println (format "Test mean error for best: %.5f" (double (/ best-total-test-error (count best-test-errors)))))
    (when (zero? (:total-error best))
      (doseq [[i error] (map vector
                             (range)
                             best-test-errors)]
        (println (format "Test Case  %3d | Error: %s" i (str error)))))
    (println ";;------------------------------")
    (println "Outputs of best individual on training cases:")
    (error-function best :train true)
    (println ";;******************************")
    )) ;; To do validation, could have this function return an altered best individual
       ;; with total-error > 0 if it had error of zero on train but not on validation
       ;; set. Would need a third category of data cases, or a defined split of training cases.


; Define the argmap
(def argmap
 {:error-function (make-mumble-error-function-from-cases (first mumble-train-and-test-cases)
                                                                     (second mumble-train-and-test-cases))
  :atom-generators mumble-atom-generators
  :max-points 1200
  :max-genome-size-in-initial-program 150
  :evalpush-limit 1500
  :population-size 1000
  :max-generations 300
  :parent-selection :lexicase
  :genetic-operator-probabilities {:alternation 0.2
                                   :uniform-mutation 0.2
                                   :uniform-close-mutation 0.1
                                   [:alternation :uniform-mutation] 0.5
                                   }
  :alternation-rate 0.01
  :alignment-deviation 10
  :uniform-mutation-rate 0.01
  :problem-specific-report mumble-report
  :problem-specific-initial-report mumble-initial-report
  :report-simplifications 0
  :final-report-simplifications 5000
  :max-error 100000
  })
