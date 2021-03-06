(ns confucius.core-test
  (:require
    [confucius.core       :as    c :refer :all]
    [confucius.proto      :as    p]
    [confucius.ext.all]
    [clojure.java.io      :as    io]
    [clojure.test         :refer :all]))

(deftest test-envify
  (testing "precedence"
    (System/setProperty "path" "from properties")
    (is (= "from context"
           (envify
             [{:path "from context"}]
             "${path}")))
    (is (= "from properties"
           (envify
             [{}]
             "${path}")))
    (System/clearProperty "path")
    (let [n (envify [{}] "${path}")]
      (is (< 1 (count n)))
      (is (not (or (= "from properties" n)
                   (= "from context" n))))))

  (testing "recursive lookups"
    (is (= "root -> node -> leaf"
           (envify
             [{:root "root"
               :node "${root} -> node"}]
             "${node} -> leaf"))))

  (testing "not found cases"
    (is (= "defaulted"
           (envify
             [{}]
             "${abc:defaulted}")))
    (is (thrown-with-msg?
          IllegalStateException
          #"Reference not found.*"
          (envify
            [{}]
            "${abc}")))))

(deftest test-from-url
  (testing "yaml"
    (let [r (p/load (io/resource "test1.yml"))]
      (is (instance?
            clojure.lang.PersistentArrayMap
            (get r :abc)))
      (is (= {:abc {:on-classpath "@:cp://test2.yml"
                    :on-fs-rel "@:file://src/test/resources/test2.yml"}}
             r))))
  (testing "json"
    (is (= {:abc {:on-classpath "@:cp://test2.yml"
                  :on-fs-rel "@:file://src/test/resources/test2.yml"}}
           (p/load (io/resource "test1.json"))))))

(deftest test-load-config
  (testing "include"
    (let [ctx [{}]
          expected {:abc
                    {:on-fs-rel {:abc 1}
                     :on-classpath {:abc 1}}}]
      ;; classpath
      (is (= expected
             (p/process
               include-value-reader
               {:value-readers *default-value-readers*}
               ctx
               "@:cp://test1.yml")))
      ;; file
      (is (= expected
             (p/process
               include-value-reader
               {:value-readers *default-value-readers*}
               ctx
               "@:file://src/test/resources/test1.yml")))))

  (testing "load-config with multiple `ConfigSource'"
    (let [cfg (load-config [{:from-map 1}
                            (io/resource "test1.yml")])]
      (is (= {:from-map 1
              :abc
              {:on-classpath
               {:abc 1}

               :on-fs-rel
               {:abc 1}}}
             cfg)))))
