namespace :sunspot do
  namespace :solr do
    desc 'Start the Solr instance'
    task :start => :environment do
      if RUBY_PLATFORM =~ /w(in)?32$/
        abort('This command does not work on Windows. Please use rake sunspot:solr:run to run Solr in the foreground.')
      end
      Sunspot::Rails::Server.new.start
    end

    desc 'Run the Solr instance in the foreground'
    task :run => :environment do
      Sunspot::Rails::Server.new.run
    end

    desc 'Stop the Solr instance'
    task :stop => :environment do
      if RUBY_PLATFORM =~ /w(in)?32$/
        abort('This command does not work on Windows.')
      end
      Sunspot::Rails::Server.new.stop
    end

    desc "Alias to sunspot:reindex"
    task :reindex => :"sunspot:reindex"

    desc "Stop/Start/Reindex solr all in one shot"
    task :reboot => :environment do
      begin
        Rake::Task[:"sunspot:solr:stop"].invoke
      rescue Sunspot::Rails::Server::NotRunningError => e
        puts e.message
        puts "No server to stop."
      ensure
        print "Starting server..."
        Rake::Task[:"sunspot:solr:start"].invoke

        # Aryk: In order to avoid "MySQL server has gone away" on sunspot:reindex, we must reestablish
        # the database connection. This is because sunspot:solr:start calls fork which I believe is
        # screwing with the connection to the DB.
        ActiveRecord::Base.establish_connection(Rails.env)

        puts "done"
      end

      print "Indexing server..."
      Rake::Task[:"sunspot:reindex"].invoke
      puts "done"
    end
  end

  desc "Reindex all solr models that are located in your application's models directory."
  # This task depends on the standard Rails file naming \
  # conventions, in that the file name matches the defined class name. \
  # By default the indexing system works in batches of 50 records, you can \
  # set your own value for this by using the batch_size argument. You can \
  # also optionally define a list of models to separated by a forward slash '/'
  # 
  # $ rake sunspot:reindex                # reindex all models
  # $ rake sunspot:reindex[1000]          # reindex in batches of 1000
  # $ rake sunspot:reindex[false]         # reindex without batching
  # $ rake sunspot:reindex[,Post]         # reindex only the Post model
  # $ rake sunspot:reindex[1000,Post]     # reindex only the Post model in
  #                                       # batchs of 1000
  # $ rake sunspot:reindex[,Post+Author]  # reindex Post and Author model
  task :reindex, [:batch_size, :models] => [:environment] do |t, args|
    reindex_options = {:batch_commit => false}
    case args[:batch_size]
    when 'false'
      reindex_options[:batch_size] = nil
    when /^\d+$/
      reindex_options[:batch_size] = args[:batch_size].to_i if args[:batch_size].to_i > 0
    end
    sunspot_models = unless args[:models]
      models_path = Rails.root.join('app', 'models')
      all_files = Dir.glob(models_path.join('**', '*.rb'))
      all_models = all_files.map { |path| path.sub(models_path.to_s, '')[0..-4].camelize.sub(/^::/, '').constantize rescue nil }.compact
      all_models.select { |m| m < ActiveRecord::Base and m.searchable? }
    else
      args[:models].split('+').map{ |m| m.constantize }
    end
    sunspot_models.each { |model| model.solr_reindex(reindex_options) }
  end
end
