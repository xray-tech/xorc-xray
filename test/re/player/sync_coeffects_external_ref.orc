def ExternalRef(id, init) =
  Coeffect({. name = "ref.register", id = id, init = init .}) >>
  def read() =
    Coeffect({. name = "ref.deref", id = id .})
  def write(x) =
    Coeffect({. name = "ref.write", id = id, value = x .})
  {. read = read, write = write .}

val counter = ExternalRef("fqcap", 0)

counter := 10 >> Coeffect({. name = "dummy" .})