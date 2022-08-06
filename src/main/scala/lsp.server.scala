package grammarsy

import langoustine.lsp.*
import cats.effect.IO
import jsonrpclib.fs2.*

import structures.*
import json.*
import requests.*
import aliases.*
import io.scalajs.nodejs.fs.*

import scala.concurrent.ExecutionContext

def server(implicit ec: ExecutionContext) =
  val state = State.create()
  LSPBuilder
    .create[IO]
    .handleRequest(initialize) { (in, back) =>
      IO {
        InitializeResult(
          ServerCapabilities(
            hoverProvider = Opt(true),
            definitionProvider = Opt(true),
            documentSymbolProvider = Opt(true),
            textDocumentSync = Opt(
              TextDocumentSyncOptions(
                openClose = Opt(true),
                save = Opt(true)
              )
            )
          ),
          Opt(
            InitializeResult
              .ServerInfo(name = "grammarsy", version = Opt("0.0.1"))
          )
        )
      }
    }
    .handleNotification(textDocument.didOpen) { (in, _) =>
      val path = in.textDocument.uri.value.drop("file://".length)
      IO.fromFuture {
        IO {
          Fs.readFileFuture(path, "utf8").map { case str: String =>
            state.index(str, in.textDocument.uri)
          }
        }
      }
    }
    .handleNotification(textDocument.didSave) { (in, _) =>
      val path = in.textDocument.uri.value.drop("file://".length)
      IO.fromFuture {
        IO {
          Fs.readFileFuture(path, "utf8").map { case str: String =>
            state.index(str, in.textDocument.uri)
          }
        }
      }
    }
    .handleRequest(textDocument.documentSymbol) { (in, back) =>
      back.notification(
        window.showMessage,
        ShowMessageParams(
          enumerations.MessageType.Error,
          "Hello from langoustine (all new)!"
        )
      ) *>
        IO {
          state.rules.toOption.toVector.flatten.map {
            case (ruleName, location) =>
              SymbolInformation(
                location = location,
                name = ruleName,
                kind = enumerations.SymbolKind.Field
              )
          }
        }
    }
    .handleRequest(textDocument.definition) { (in, back) =>
      IO {
        val loc = state.ruleDefinition(in.position).toOption.flatten

        loc match
          case None                => Definition(Vector.empty)
          case Some((ruleName, v)) => Definition(v)
      }
    }
    .handleRequest(textDocument.hover) { (in, back) =>
      IO {
        val loc = state.ruleHover(in.position).toOption.flatten

        loc match
          case None => Nullable.NULL
          case Some((ruleName, contents)) =>
            Nullable {
              Hover(contents =
                Vector(
                  MarkedString(s"Reduction `$ruleName`"),
                  MarkedString(MarkedString.S0("javascript", contents))
                )
              )
            }
        end match
      }
    }
end server
