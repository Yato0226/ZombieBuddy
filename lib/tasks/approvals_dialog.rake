# frozen_string_literal: true

# ZombieBuddy mod root: lib/tasks -> .. -> ..
ZB_MOD_ROOT = File.expand_path("../..", __dir__)
ZB_JAR = File.join(ZB_MOD_ROOT, "java/build/libs/ZombieBuddy.jar")
ZB_MAIN = "me.zed_0xff.zombie_buddy.BatchJarApprovalMain"

def zb_find_java
  if (h = ENV["JAVA_HOME"]) && !h.empty?
    exe = File.join(h, "bin/java")
    return exe if File.executable?(exe)
  end
  return "java" if system("command -v java >/dev/null 2>&1")
  raise "java not found; set JAVA_HOME or install a JDK"
end

def zb_ensure_jar!
  return ZB_JAR if File.exist?(ZB_JAR)

  puts "Building #{ZB_JAR} ..."
  Dir.chdir(File.join(ZB_MOD_ROOT, "java")) { sh "gradle jar -q" }
  raise "jar missing after build: #{ZB_JAR}" unless File.exist?(ZB_JAR)

  ZB_JAR
end

# ZB_BATCH_V5 request body (see JarBatchApprovalProtocol.writeRequest).
def zb_sample_batch_request_v5(entries)
  lines = [ "ZB_BATCH_V5", entries.size.to_s ]
  entries.each do |e|
    lines << "---"
    lines << e[:mod_key]
    lines << e[:mod_id]
    lines << e[:jar_path]
    lines << e[:sha256]
    lines << e[:modified]
    lines << e[:prior_hint].to_s
    lines << e[:mod_display_name].to_s
    lines << e[:zbs_valid].to_s
    lines << e[:zbs_steam_id].to_s
    lines << e[:zbs_notice].to_s
    lines << e[:steam_ban_status].to_s
    lines << e[:steam_ban_reason].to_s
  end
  lines.join("\n") + "\n"
end

namespace :zb do
  desc "Run the Swing batch Java-mod approvals dialog without launching Project Zomboid. " \
       "Uses a sample request (two demo mods). Response is printed after OK."
  task :approvals_dialog do
    require "tmpdir"

    jar = zb_ensure_jar!
    java = zb_find_java

    hex64 = "a" * 64

    sample = 3.times.map do |i|
      {
        mod_key: "DemoModOk#{i}",
        mod_id: "DemoModOk#{i}",
        jar_path: "/tmp/DemoModOk#{i}/media/java/client/DemoModOk.jar",
        sha256: hex64,
        modified: "2025-01-01",
        prior_hint: "",
        mod_display_name: "Signed OK",
        zbs_valid: "yes",
        zbs_steam_id: "76561198000000001",
        zbs_notice: "",
        steam_ban_status: "no",
        steam_ban_reason: ""
      }
    end + [
      {
        mod_key: "DemoModBad",
        mod_id: "DemoModBad",
        jar_path: "/tmp/DemoModBad/media/java/client/DemoModBad.jar",
        sha256: "b" * 64,
        modified: "2025-01-02",
        prior_hint: "",
        mod_display_name: "Tampered",
        zbs_valid: "no",
        zbs_steam_id: "",
        zbs_notice: "Invalid signature — JAR may have been tampered with.",
        steam_ban_status: "no",
        steam_ban_reason: ""
      },
      {
        mod_key: "DemoModBanned",
        mod_id: "DemoModBanned",
        jar_path: "/tmp/DemoModBanned/media/java/client/DemoModBanned.jar",
        sha256: "d" * 64,
        modified: "2025-01-03",
        prior_hint: "",
        mod_display_name: "Banned on Workshop",
        zbs_valid: "yes",
        zbs_steam_id: "76561198000001000",
        zbs_notice: "",
        steam_ban_status: "yes",
        steam_ban_reason: "Steam moderation flag."
      },
      {
        mod_key: "DemoModUnknownBan",
        mod_id: "DemoModUnknownBan",
        jar_path: "/tmp/DemoModUnknownBan/media/java/client/DemoModUnknownBan.jar",
        sha256: "e" * 64,
        modified: "2025-01-04",
        prior_hint: "",
        mod_display_name: "Ban status unknown",
        zbs_valid: "yes",
        zbs_steam_id: "76561198000001001",
        zbs_notice: "",
        steam_ban_status: "unknown",
        steam_ban_reason: "Steam API request failed (HTTP 503)."
      },
      {
        mod_key: "DemoModLegacy",
        mod_id: "DemoModLegacy",
        jar_path: "/tmp/DemoModLegacy/media/java/client/DemoModLegacy.jar",
        sha256: "c" * 64,
        modified: "2024-12-01",
        prior_hint: "",
        mod_display_name: "No ZBS fields",
        zbs_valid: "",
        zbs_steam_id: "",
        zbs_notice: "",
        steam_ban_status: "unknown",
        steam_ban_reason: "No workshop id found in path."
      }
    ]

    Dir.mktmpdir("zb-approval-dialog-") do |dir|
      req = File.join(dir, "request.txt")
      resp = File.join(dir, "response.txt")
      File.write(req, zb_sample_batch_request_v5(sample))

      cmd = [
        java, "-Djava.awt.headless=false",
        "-cp", jar,
        ZB_MAIN,
        req, resp
      ]
      puts cmd.join(" ")
      system(*cmd)
      st = $?.exitstatus
      if st != 0
        warn "BatchJarApprovalMain exited #{st} (cancel = 2)"
        exit st if st
      end
      if File.exist?(resp)
        puts "--- response (#{resp}) ---"
        puts File.read(resp)
      else
        warn "No response file at #{resp}"
      end
    end
  end
end
