(ns usage-insights.read-config
  (:require [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defn find-claude-md-paths
  "Return a vector containing the CLAUDE.md path if it exists, else empty vector."
  [claude-dir]
  (let [f (io/file claude-dir "CLAUDE.md")]
    (if (.exists f) [(str f)] [])))

(defn find-hooks
  "Return sorted, deduplicated list of hook names from hooks/*.sh files and settings.json."
  [claude-dir]
  (let [hooks-dir     (io/file claude-dir "hooks")
        settings-file (io/file claude-dir "settings.json")
        from-dir      (if (.isDirectory hooks-dir)
                        (->> (.listFiles hooks-dir)
                             (filter #(.. % getName (endsWith ".sh")))
                             (map #(.getName %)))
                        [])
        from-settings (if (.exists settings-file)
                        (try
                          (->> (json/parse-string (slurp settings-file) true)
                               :hooks
                               (mapcat :hooks)
                               (keep :name))
                          (catch Exception e
                            (binding [*out* *err*]
                              (println (str "WARNING: failed to parse settings.json at " (str settings-file) ": " (.getMessage e))))
                            []))
                        [])]
    (vec (sort (distinct (concat from-dir from-settings))))))

(defn find-skills
  "Return sorted, deduplicated list of skill names from plugins/cache/**/SKILL.md files."
  [claude-dir]
  (let [cache-dir (io/file claude-dir "plugins" "cache")]
    (if-not (.exists cache-dir)
      []
      (->> (file-seq cache-dir)
           (filter #(= "SKILL.md" (.getName %)))
           (map #(.. % getParentFile getName))
           distinct
           sort
           vec))))

(defn read-config
  "Return config inventory map. Pure function for testability."
  [claude-dir]
  {:claude_md_paths (find-claude-md-paths claude-dir)
   :hooks           (find-hooks claude-dir)
   :skills          (find-skills claude-dir)})

(defn -main
  "CLI entry point. Accepts --claude-dir flag; defaults to ~/.claude."
  [& args]
  (let [opts      (apply hash-map args)
        claude-dir (io/file (get opts "--claude-dir" (str (System/getProperty "user.home") "/.claude")))
        inventory  (read-config claude-dir)]
    (println (json/generate-string inventory {:pretty true}))))
