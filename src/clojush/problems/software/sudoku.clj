;; sudoku.clj
;; Tom Helmuth, thelmuth@cs.umass.edu
;;

(ns clojush.problems.software.sudoku
  (:use clojush.pushgp.pushgp
        [clojush pushstate interpreter random util globals]
        clojush.instructions.tag
        [clojure.math numeric-tower]
        ))

; Atom generators
(def sudoku-atom-generators
  (concat (list
            ;;; end constants
            ;;; end ERCs
            (tag-instruction-erc [:integer :exec :string :char :vector_string] 1000)
            (tagged-instruction-erc 1000)
            ;;; end tag ERCs
            'in1
            ;;; end input instructions
            )
          (registered-for-stacks [:integer :exec :string :char :vector_string])))


;; A list of data domains for the problem. Each domain is a vector containing
;; a "set" of inputs and two integers representing how many cases from the set
;; should be used as training and testing cases respectively. Each "set" of
;; inputs is either a list or a function that, when called, will create a
;; random element of the set.
(def sudoku-data-domains
  [[(list [0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0] ; All gutter balls
          [10 10 10 10 10 10 10 10 10 10 10 10] ; All strikes
          [5 5 5 5 5 5 5 5 5 5 5 5 5 5 5 5 5 5 5 5 5] ; All spares
          [7 1 1 5 10 10 10 5 4 8 2 2 7 9 0 10 5 3] ; Ending with a strike
          [5 3 2 8 4 3 6 2 10 1 7 9 0 4 1 4 4 7 3 5]  ; Ending with a spare
          [2 4 8 1 5 3 6 1 3 5 6 2 1 2 8 1 3 5 8 1]   ; No strikes, no spares
          [0 0 0 0 0 0 10 0 0 0 0 0 0 0 0 0 0 0 0]  ; One strike, nothing else
          [0 0 0 0 0 0 0 0 0 0 3 7 0 0 0 0 0 0 0 0]) 8 0] ; One spare, nothing else
   [(fn [] (vec (repeatedly 81 #(inc (lrand-int 10))))) 92 1000]
  ])

;;Can make sudoku test data like this:
;(test-and-train-data-from-domains sudoku-data-domains)


; Helper function for the below helper function
; Cite: Some code from https://gist.github.com/keelerm84/9037640
(defn make-frames [rolls]
 (let [must-take (if (or (= 10 (reduce + (take 2 rolls))) (= 10 (first rolls))) 3 2)
       must-drop (if (= 10 (first rolls)) 1 2)]
   (lazy-seq
    (cons
     (take must-take rolls)
     (make-frames (drop must-drop rolls))))))

; Helper function for error function
(defn sudoku-test-cases
  "Takes a sequence of inputs and gives IO test cases of the form
   [input output]."
  [inputs]
  (map (fn [in]
         (vector in
           (reduce + (map #(reduce + %) (take 10 (make-frames in))))))
       inputs))

(defn make-sudoku-error-function-from-cases
  [train-cases test-cases]
  (fn the-actual-sudoku-error-function
    ([individual]
      (the-actual-sudoku-error-function individual :train))
    ([individual data-cases] ;; data-cases should be :train or :test
     (the-actual-sudoku-error-function individual data-cases false))
    ([individual data-cases print-outputs]
      (let [behavior (atom '())
            errors (doall
                     (for [[input1 correct-output] (case data-cases
                                                     :train train-cases
                                                     :test test-cases
                                                     [])]
                       (let [final-state (run-push (:program individual)
                                                   (->> (make-push-state)
                                                     (push-item input1 :input)))
                             result (stack-ref :integer 0 final-state)]
                         (when print-outputs
                           (println (format "Correct output: %6d | Program output: %s" correct-output (str result))))
                         ; Record the behavior
                         (swap! behavior conj result)
                         ; Error is integer distance
                         (if (number? result)
                           (abs (- result correct-output)) ;distance from correct integer
                           1000000000) ;penalty for no return value
                         )))]
        (if (= data-cases :train)
          (assoc individual :behaviors @behavior :errors errors)
          (assoc individual :test-errors errors))))))

(defn get-sudoku-train-and-test
  "Returns the train and test cases."
  [data-domains]
  (map sort (map sudoku-test-cases
                 (test-and-train-data-from-domains data-domains))))

; Define train and test cases
(def sudoku-train-and-test-cases
  (get-sudoku-train-and-test sudoku-data-domains))

(defn sudoku-initial-report
  [argmap]
  (println "Train and test cases:")
  (doseq [[i case] (map vector (range) (first sudoku-train-and-test-cases))]
    (println (format "Train Case: %3d | Input/Output: %s" i (str case))))
  (doseq [[i case] (map vector (range) (second sudoku-train-and-test-cases))]
    (println (format "Test Case: %3d | Input/Output: %s" i (str case))))
  (println ";;******************************"))

(defn sudoku-report
  "Custom generational report."
  [best population generation error-function report-simplifications]
  (let [best-test-errors (:test-errors (error-function best :test))
        best-total-test-error (apply +' best-test-errors)]
    (println ";;******************************")
    (printf ";; -*- sudoku problem report - generation %s\n" generation)(flush)
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
  {:error-function (make-sudoku-error-function-from-cases (first sudoku-train-and-test-cases)
                                                                  (second sudoku-train-and-test-cases))
   :atom-generators sudoku-atom-generators
   :max-points 1600
   :max-genome-size-in-initial-program 200
   :evalpush-limit 4000
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
   :problem-specific-report sudoku-report
   :problem-specific-initial-report sudoku-initial-report
   :report-simplifications 0
   :final-report-simplifications 5000
   :max-error 1000000000
   })
