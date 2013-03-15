package molmed.queue.setup
import java.io.File

case class ReadPairContainer(mate1: File, mate2: File = null, var sampleName: String = null) {
	def isMatePaired(): Boolean = {mate2 != null}	
}