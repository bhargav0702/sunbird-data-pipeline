require 'elasticsearch'
require 'pry'

module Indexers
  class Elasticsearch
    TEMPLATE_MAPPINGS = {
          devices_v1: {
            _id: {
              path: "did"
            },
            properties: {
              did: { type: 'string', index: 'not_analyzed'}
            }
          },
          _default_: {
            dynamic_templates: [
            {
              string_fields: {
                mapping: {
                  index: "not_analyzed",
                  omit_norms: false,
                  type: "string",
                  doc_values: true
                },
                match_mapping_type: "string",
                match: "*"
              }
            },
            {
              string_fields_force: {
                mapping: {
                  index: "not_analyzed",
                  omit_norms: false,
                  type: "string",
                  doc_values: true
                },
                match: "id|current|res|exres|max|mc|mmc|category",
                match_pattern: "regex"
              }
            },
            {
              double_fields: {
                  match: "mem|idisk|edisk|scrn|length|exlength|age|percent_correct|percent_attempt|size|score|maxscore",
                  match_pattern: "regex",
                  mapping: {
                      type: "double",
                      index: "not_analyzed",
                      doc_values: true
                  }
              }
            },
            {
             boolean_fields:{
                  match: "signup_processed",
                  match_pattern: "regex",
                  mapping: {
                      type: "boolean",
                      index: "not_analyzed",
                      doc_values: true
                  }
              }
            },
            {
              integer_fields: {
                  match: "sims|atmpts|failedatmpts|correct|incorrect|total|age_completed_years",
                  match_pattern: "regex",
                  mapping: {
                      type: "integer",
                      index: "not_analyzed",
                      doc_values: true
                  }
              }
            },
            {
              date_fields: {
                  match: "ts|te|time",
                  match_pattern: "regex",
                  mapping: {
                      type: "date",
                      index: "not_analyzed",
                      doc_values: true
                  }
              }
            },
            {
              geo_location: {
                  mapping: {
                      type: "geo_point",
                      doc_values: true
                  },
                  match: "loc"
              }
            }
            ],
            properties: {
              geoip: {
                dynamic: true,
                path: "full",
                properties: {
                  location: {
                    type: "geo_point"
                  }
                },
                type: "object"
              },
              "@version" => {
                index: "not_analyzed",
                type: "string"
              }
            },
            _all: {
              enabled: true
            }
          }
        }
    DUMP_MAPPINGS = {
          _default_: {
            dynamic_templates: [
            {
              string_fields: {
                mapping: {
                  index: "not_analyzed",
                  omit_norms: false,
                  type: "string",
                  doc_values: true
                },
                match_mapping_type: "string",
                match: "*"
              }
            },
            {
              string_fields_force: {
                mapping: {
                  index: "not_analyzed",
                  omit_norms: false,
                  type: "string",
                  doc_values: true
                },
                match: "current|res|exres|max|mc|mmc|category",
                match_pattern: "regex"
              }
            },
            {
              date_fields: {
                  match: "ts|te|time|reset-time",
                  match_pattern: "regex",
                  mapping: {
                      type: "date",
                      index: "not_analyzed",
                      doc_values: true
                  }
              }
            },
            {
              geo_location: {
                  mapping: {
                      type: "geo_point",
                      doc_values: true
                  },
                  match: "loc"
              }
            }
            ],
            _all: {
              enabled: true
            }
          }
        }
    attr_reader :client
    def initialize(refresh=true)
      @client = ::Elasticsearch::Client.new log: false
      if refresh
        delete_templates
        create_templates
      end
    end
    def delete_templates
      client.indices.delete_template name: 'dump' rescue nil
      client.indices.delete_template name: 'ecosystem' rescue nil
    end
    def create_templates
      puts client.indices.put_template({
        name: "dump",
        body: {
        order: 20,
        template: "dump",
        settings: {
          # "index.refresh_interval": "5s"
        },
        mappings: DUMP_MAPPINGS,
        aliases: {}
        }
      })
      puts client.indices.put_template({
        name: "ecosystem",
        body: {
        order: 10,
        template: "ecosystem-*",
        settings: {
          "index.refresh_interval": "5s"
        },
        mappings: TEMPLATE_MAPPINGS,
        aliases: {}
        }
      })
    end
    def get(index,type,id)
      begin
        binding.pry
        @client.get({
              index: index,
              type: type,
              id: id
              }
            )
      rescue => e
        raise e
      end
    end
    def index(index,type,body)
      begin
        @client.index({
              index: index,
              type: type,
              body: body
              }
            )
      rescue => e
        raise e
      end
    end
    def update(index,type,id,body)
      begin
        @client.update({
                index: index,
                type: type,
                id: id,
                body: { doc: body }
        })
      rescue => e
        raise e
      end
    end
  end
end
