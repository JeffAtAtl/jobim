(ns jobim.test.jobim
  (:use [jobim] :reload)
  (:use [jobim.examples.actors] :reload)
  (:use [clojure.test]))



(println "*********************************************************************************************")
(println "***********      To run these tests an instance of Zookeeper, RabbitMQ 2.0          *********")
(println "***********      and a remote node correctly configured must be running             *********")
(println "*********************************************************************************************")

(defonce *remote-node-name* "remote-test")
(defonce *local-node-name* "local-test")
(defonce *test-node-config* {:node-name "local-test"
                             :rabbit-options []
                             :zookeeper-options ["localhost:2181" {:timeout 3000}]})
(defonce *test-node-name*  "local-test")

(bootstrap-node (:node-name *test-node-config*) (:rabbit-options *test-node-config*) (:zookeeper-options *test-node-config*))

(spawn-in-repl)

(deftest test-spawn
  (println "*** test-spawn")
  (let [pid (spawn jobim.examples.actors/ping)]
    (is (not (nil? pid)))))

(deftest test-spawn-from-string
  (println "*** test-spawn-from-string")
  (let [pid (spawn "jobim.examples.actors/ping")]
    (is (not (nil? pid)))))


(deftest test-remote-spawn-plus-register-name-test-send
  (println "*** test-remote-spawn-plus-register-name-test-send")
  (let [pid1 (rpc-blocking-call (resolve-node-name *remote-node-name*) "jobim/spawn" ["jobim.examples.actors/ping"])]
    (rpc-blocking-call (resolve-node-name *remote-node-name*) "jobim/register-name" ["remoteping" pid1])
    (is (= pid1 (get (registered-names) "remoteping")))
    (let [prom (promise)
          prom2 (promise)
          prom3 (promise)
          pid2 (spawn (fn [] (let [m (receive)] (deliver prom m))))
          pid3 (spawn (fn [] (link (resolve-name "remoteping"))
                        (deliver prom2 "go on")
                        (let [m (receive)] (deliver prom3 m))))]
      (send! (resolve-name "remoteping") [pid2 "hey"])
      (is (= "hey" @prom))
      @prom2
      (send! (resolve-name "remoteping") "exception")
      (is (= (:signal @prom3) :link-broken)))))


(deftest test-send
  (println "*** test-send")
  (let [prom (promise)
        pid1 (spawn jobim.examples.actors/ping)
        pid2 (spawn (fn [] (let [m (receive)] (deliver prom m))))]
    (send! pid1 [pid2 "hey"])
    (is (= "hey" @prom))))


(deftest test-link
  (println "*** test-link")
  (let [prom (promise)
        pid1 (spawn jobim.examples.actors/ping)
        pid2 (spawn (fn [] (link pid1) (send! pid1 "exception") (let [m (receive)] (deliver prom (:signal m)))))]
    (is (= @prom :link-broken))))

(deftest test-evented-actor
  (println "*** test-evented-actor")
  (let [prom (promise)
        evt (spawn-evented jobim.examples.actors/ping-evented)
        pid (spawn (fn [] (let [m (receive)] (deliver prom m))))]
    (send! evt [pid "hey"])
    (is (= "actor test evented says hey" @prom))))