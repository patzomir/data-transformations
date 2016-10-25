#!/usr/bin/env perl

use strict;
use warnings;

use HTTP::Tiny;

#####################################################################
# Fetch a list of EAD2002 elements and their possible parents from: #
# https://www.loc.gov/ead/tglib/index.html                          #
#####################################################################

# fetch the list of elements
my $url = "https://www.loc.gov/ead/tglib/element_index.html";
my $response = HTTP::Tiny->new()->get($url);
die "STATUS $response->{status}: $url" unless $response->{success};

# fetch the page for each element
foreach my $element ($response->{content} =~ m/href="elements\/([a-zA-Z0-9]+)\.html"/g) {
    my $element_url = "https://www.loc.gov/ead/tglib/elements/$element.html";
    my $element_response = HTTP::Tiny->new()->get($element_url);
    warn "STATUS $element_response->{status}: $element_url" and next unless $element_response->{success};

    # extract the parents of the element
    my ($element_parents) = $element_response->{content} =~ m/<[^>]+>May occur within:<\/[^>]+>[^<]*<[^>]+>([^<]*)<\/[^>]+>/;
    warn "WARN: failed to extract the parents of \"$element\"" and next unless $element_parents;

    # print the element and its parents
    print "$element => " . join(" | ", split(/[^a-zA-Z0-9]+/, $element_parents)) . "\n";
}

