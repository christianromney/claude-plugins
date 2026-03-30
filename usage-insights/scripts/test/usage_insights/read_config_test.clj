(ns usage-insights.read-config-test
  (:require [clojure.test :refer [deftest is]]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [usage-insights.read-config :as sut])
  (:import [java.nio.file Files]
           [java.nio.file.attribute FileAttribute]))

(defn- create-temp-dir []
  (str (Files/createTempDirectory "ui-config-test" (into-array FileAttribute []))))

(defn- touch [path]
  (io/make-parents path)
  (spit path ""))

(deftest find-claude-md-paths-finds-global-md
  (let [dir (create-temp-dir)]
    (spit (str dir "/CLAUDE.md") "# Config")
    (is (some #{(str dir "/CLAUDE.md")} (sut/find-claude-md-paths dir)))))

(deftest find-claude-md-paths-missing-returns-empty
  (is (= [] (sut/find-claude-md-paths (create-temp-dir)))))

(deftest find-hooks-finds-sh-files
  (let [dir (create-temp-dir)]
    (touch (str dir "/hooks/credential-guard.sh"))
    (touch (str dir "/hooks/pre-commit.sh"))
    (let [result (sut/find-hooks dir)]
      (is (some #{"credential-guard.sh"} result))
      (is (some #{"pre-commit.sh"} result)))))

(deftest find-hooks-reads-settings-json
  (let [dir (create-temp-dir)]
    (touch (str dir "/hooks/.keep"))
    (spit (str dir "/settings.json")
          (json/generate-string
           {:hooks [{:matcher "Bash"
                     :hooks [{:type "command" :command "run" :name "my-hook"}]}]}))
    (is (some #{"my-hook"} (sut/find-hooks dir)))))

(deftest find-hooks-missing-dirs-returns-empty
  (is (= [] (sut/find-hooks (create-temp-dir)))))

(deftest find-skills-finds-skill-md-names
  (let [dir (create-temp-dir)]
    (touch (str dir "/plugins/cache/my-plugin/1.0.0/skills/my-skill/SKILL.md"))
    (is (some #{"my-skill"} (sut/find-skills dir)))))

(deftest find-skills-deduplicates-across-versions
  (let [dir (create-temp-dir)]
    (touch (str dir "/plugins/cache/my-plugin/1.0.0/skills/my-skill/SKILL.md"))
    (touch (str dir "/plugins/cache/my-plugin/1.0.1/skills/my-skill/SKILL.md"))
    (let [result (sut/find-skills dir)]
      (is (= 1 (count (filter #{"my-skill"} result)))))))

(deftest find-skills-missing-plugins-returns-empty
  (is (= [] (sut/find-skills (create-temp-dir)))))

(deftest read-config-returns-combined-inventory
  (let [dir (create-temp-dir)]
    (spit (str dir "/CLAUDE.md") "# Config")
    (touch (str dir "/hooks/my-hook.sh"))
    (touch (str dir "/plugins/cache/p/1.0/skills/my-skill/SKILL.md"))
    (let [result (sut/read-config dir)]
      (is (some #{(str dir "/CLAUDE.md")} (:claude_md_paths result)))
      (is (some #{"my-hook.sh"} (:hooks result)))
      (is (some #{"my-skill"} (:skills result))))))
