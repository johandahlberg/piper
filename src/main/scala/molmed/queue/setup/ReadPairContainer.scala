package molmed.queue.setup
import java.io.File

/**
 * Container class for read pairs
 * 
 * Written in the early days of Piper, before learning Scala well, therefore contains some rather
 * ugly design choices. /JD
 * 
 * @todo It would be better if the mates were mate options instead of using null.
 * @todo It would be better if the sampleName was not mutable.
 */
case class ReadPairContainer(mate1: File, mate2: File = null, var sampleName: String = null) {
	def isMatePaired(): Boolean = {mate2 != null}	
}