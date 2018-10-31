def ReceiveEvent(pattern) = Coeffect({. kind = "subscription", pattern = pattern .})
  
def ReceiveCheckout() = ReceiveEvent("\\{.+\\}\\{.+\\}\\{events\\.SDKEvent\\}\\{checkout\\}")

def property(ev, prop) =
  def find([]) = null
  def find(({. key = key .} as p):_) if (key = prop) =
    p.stringValue | p.numberValue | p.boolValue

  def find(_:xs) = find(xs)
  find(ev.event.properties)
  

def loop(iteration) =
  ReceiveCheckout() >ev>
  ("iteration: " + iteration + "; sum: " + property(ev, "sum") |
    loop(iteration + 1))

loop(0)
