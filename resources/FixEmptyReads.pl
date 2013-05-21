#!/usr/bin/perl

#this script reads a fastq.gz-file and changes empty reads to 'N' with quality '@'
use strict;
use warnings;
use Getopt::Long;
use FileHandle;


#perl FixEmptyReads.pl -i MyFastqWithEmptyReads.fastq.gz -o MyFastqWithNsInstead.fastq.gz

my($input,$output);
GetOptions("i=s" => \$input,"o=s" => \$output);

# define filehandles for printing mates
open OUTPUT, "| gzip > $output" or die("cannot open file to write to. $!");

open(INPUT, "zcat $input |") or die("cannot read file, not zipped? $!");

while(<INPUT>) {

    chomp;
    my $name = $_;
    my $seq = <INPUT>;
    chomp($seq);
    my $name2= <INPUT>;
    chomp($name2);
    my $qual = <INPUT>;
    chomp $qual;
    if ($seq eq ""){
	$seq = "N";
	$qual = "\@";
    }
    print OUTPUT "$name\n$seq\n$name2\n$qual\n";
}

close INPUT;
close OUTPUT;

