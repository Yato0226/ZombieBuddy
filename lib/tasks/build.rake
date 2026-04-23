desc 'build'
task :build => ["unstable:build", "clean", "42_12:build"]

# runs first
namespace "unstable" do
  desc 'build'
  task :build => :chdir do
    env = {
      "JAVA_HOME" => "/Library/Java/JavaVirtualMachines/openjdk-24.jdk/Contents/Home"
    }
    cp_root = File.join(PROJECT_ROOT, "versions/unstable/java")
    cp = [File.join(cp_root, "projectzomboid.jar")].join(",")

    sh env, "gradle build --warning-mode all -PgameClasspath=#{cp}"
  end
end

# runs last, result is the final build
namespace "42_12" do
  desc 'build'
  task :build => :chdir do
    env = {
      "JAVA_HOME" => "/Library/Java/JavaVirtualMachines/openjdk-17.jdk/Contents/Home"
    }
    cp_root = File.join(PROJECT_ROOT, "versions/42.12/java")
    cp = [
      cp_root,
      File.join(cp_root, "lwjgl.jar"),
      File.join(cp_root, "lwjgl-glfw.jar"),
    ].join(",")

    sh env, "gradle build --warning-mode all -PgameClasspath=#{cp}"
  end
end

