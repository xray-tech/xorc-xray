def ReceiveEvent(pattern) = Coeffect({. kind = "subscription", pattern = pattern .})
  
def loop() =
  ReceiveEvent("\\{.+\\}\\{.+\\}\\{events\\.SDKEvent\\}\\{bench-event\\}") >> loop()

loop()
