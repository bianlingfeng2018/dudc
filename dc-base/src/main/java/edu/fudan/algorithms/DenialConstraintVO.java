package edu.fudan.algorithms;

import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author Lingfeng
 */
@AllArgsConstructor
@Getter
public class DenialConstraintVO {

    private String originalString;

    private Set<PredicateVO> predicateVOList;

}
