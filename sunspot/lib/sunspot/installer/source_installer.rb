require 'fileutils'

module Sunspot
  class Installer
    class SourceInstaller
      class <<self
        def execute(source_path, options)
          new(source_path, options).execute
        end
      end

      def initialize(source_path, options)
        @source_path = source_path
        @verbose = !!options[:verbose]
        @force = !!options[:force]
      end

      def execute
        sunspot_source_path = File.join(File.dirname(__FILE__), '..', '..',
                                         '..', 'solr', 'solr', 'src')
        return if File.expand_path(sunspot_source_path) == File.expand_path(@source_path)
        FileUtils.mkdir_p(@source_path)
        Dir.glob(File.join(sunspot_source_path, '*.java')).each do |java|
          java = File.expand_path(java)
          dest = File.join(@source_path, File.basename(java))
          if File.exist?(dest)
            if @force
              say("Removing existing library #{dest}")
            else
              next
            end
          end
          say("Copying #{java} => #{dest}")
          FileUtils.cp(java, dest)
        end
      end

      def say(message)
        if @verbose
          STDOUT.puts(message)
        end
      end
    end
  end
end
